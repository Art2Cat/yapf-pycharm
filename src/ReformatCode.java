import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.*;

public class ReformatCode extends AnAction {

    private Logger logger;

    public ReformatCode() {
        super();
        this.logger = Logger.getInstance(ReformatCode.class);
    }

    private byte[] toByteArray(InputStream inputStream) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        int read;
        byte[] bytes = new byte[1024];

        while ((read = inputStream.read(bytes)) != -1) {
            byteArrayOutputStream.write(bytes, 0, read);
        }

        return byteArrayOutputStream.toByteArray();
    }

    private byte[] getProcessStdout(Process p) throws IOException {
        return toByteArray(p.getInputStream());
    }

    private byte[] getProcessStderr(Process p) throws IOException {
        return toByteArray(p.getErrorStream());
    }

    private byte[] reformatFile(String path) throws InterruptedException, IOException {
        Process p = Runtime.getRuntime().exec(new String[]{
                "sh", "-c",
                String.format("/usr/local/bin/yapf '%s'", path)
        });
        p.waitFor();

        if (p.exitValue() != 0) {
            logger.error(getProcessStderr(p));
            throw new RuntimeException("Couldn't invoke reformat command");
        }

        // read the formatted content
        return getProcessStdout(p);
    }

    private void writeFileContent(InputStream inputStream, OutputStream outputStream) throws IOException {
        int read;
        byte[] bytes = new byte[1024];

        while ((read = inputStream.read(bytes)) != -1) {
            outputStream.write(bytes, 0, read);
        }
    }

    @Override
    public void actionPerformed(AnActionEvent event) {
        // extract current open file, it could be file or folder or null it doesn't get focus
        VirtualFile virtualFile = event.getData(PlatformDataKeys.VIRTUAL_FILE);

        if (virtualFile == null || virtualFile.isDirectory()) {
            return;
        }

        String path = virtualFile.getPath();

        if (!path.endsWith(".py")) {
            return;
        }

        if (!virtualFile.isWritable()) {
            return;
        }

        try {
            // save changes so that IDE doesn't display message box
            FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
            Document document = fileDocumentManager.getDocument(virtualFile);
            fileDocumentManager.saveDocument(document);

            // reformat it using Google/YAPF
            byte[] formattedContent = this.reformatFile(virtualFile.getPath());

            // unlock the file & write changes
            Application app = ApplicationManager.getApplication();
            app.runWriteAction(() -> {
                try {
                    virtualFile.setBinaryContent(formattedContent);
                    virtualFile.refresh(false, false);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
