package com.cyx.UploadToPgyPlugin;

import com.google.gson.Gson;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import okhttp3.*;
import okio.Buffer;
import okio.BufferedSink;
import okio.Okio;
import okio.Source;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.concurrent.TimeUnit;

public class UploadApk extends AnAction {


    private static final OkHttpClient okHttpClient = new OkHttpClient.Builder().readTimeout(100, TimeUnit.SECONDS)//设置读取超时时间
            .writeTimeout(100, TimeUnit.SECONDS)//设置写的超时时间
            .connectTimeout(100, TimeUnit.SECONDS)//设置连接超时时间
            .build();

    private Call call;

    @Override
    public void actionPerformed(AnActionEvent anActionEvent) {
        Project project = anActionEvent.getData(PlatformDataKeys.PROJECT);
        PsiFile currentEditorFile = anActionEvent.getData(PlatformDataKeys.PSI_FILE);
        if (currentEditorFile != null) {
            VirtualFile vfile = currentEditorFile.getVirtualFile();
            PropertiesComponent propertiesComponent = PropertiesComponent.getInstance(project);
            // set & get
//            if (!propertiesComponent.isValueSet("api_key")) {
//                propertiesComponent.setValue("api_key", "");
//            }

            String api_key = Messages.showInputDialog(project, "Please input your API KEY", "Input your api key",
                    Messages.getQuestionIcon(), propertiesComponent.getValue("api_key"), new InputValidator() {
                @Override
                public boolean checkInput(@NlsSafe String s) {
                    if (s==null || s.trim().isEmpty()) {
                        return false;
                    }
                    return true;
                }

                @Override
                public boolean canClose(@NlsSafe String s) {
                    return true;
                }
            });

            if (api_key==null || api_key.isEmpty()) {
                return;
            }


            propertiesComponent.setValue("api_key", api_key);
            String packageName = "";
            String password = "";
//            try {
//                Resources.Package apkpackage = Resources.Package.parseFrom(new FileInputStream(vfile.getPath()));
//                packageName = apkpackage.getPackageName();
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
            if (propertiesComponent.isValueSet("password")) {
                password = propertiesComponent.getValue("password");
            }

            String pw = Messages.showInputDialog(project, "Please input your PASSWORD", "Input your password(Nullable)", Messages.getQuestionIcon(), password, new InputValidator() {
                @Override
                public boolean checkInput(@NlsSafe String s) {
                    return true;
                }

                @Override
                public boolean canClose(@NlsSafe String s) {
                    return true;
                }
            });

            propertiesComponent.setValue("password", pw);


            ProgressManager.getInstance().run(new Task.Backgroundable(project, "正在上传...") {
                public void run(@NotNull ProgressIndicator progressIndicator) {
                    // start your process
                    progressIndicator.setText("上传中!");
                    progressIndicator.setIndeterminate(false);
                    MultipartBody.Builder builder = new MultipartBody.Builder().setType(MultipartBody.FORM);
                    File file = new File(vfile.getPath());
                    builder.addFormDataPart("file", file.getName(), createCustomRequestBody(MultipartBody.FORM, file, new ProgressListener() {
                        @Override
                        public void onProgress(long totalBytes, long remainingBytes, boolean done) {
                            double progress = (totalBytes - remainingBytes) / (double) totalBytes;
                            progressIndicator.setFraction(progress);
                            progressIndicator.setText(readableFileSize(totalBytes - remainingBytes) + "/" + readableFileSize(totalBytes) + "   " + (int) ((totalBytes - remainingBytes) * 100 / totalBytes) + "%");
                            try {
                                progressIndicator.checkCanceled();
                            } catch (ProcessCanceledException e) {
                                e.printStackTrace();
                                call.cancel();
                            }
                        }
                    }));
                    builder.addFormDataPart("_api_key", api_key);
                    builder.addFormDataPart("buildInstallType", pw == null || pw.length() == 0 ? "1" : "2");
                    if (pw != null && pw.length() != 0) {
                        builder.addFormDataPart("buildPassword", pw);
                    }
                    RequestBody requestBody = builder.build();
                    Request request = new Request.Builder().url("https://www.pgyer.com/apiv2/app/upload") //地址
                            .post(requestBody).build();
                    Response response = null;
                    try {
                        call = okHttpClient.newCall(request);
                        response = call.execute();
                        PgyResponse pgyResponse = new Gson().fromJson(response.body().string(), PgyResponse.class);
                        response.close();
                        if (pgyResponse.getCode() == 0) {
                            progressIndicator.setText("上传成功!");
                            ApplicationManager.getApplication().invokeLater(() -> {
                                Messages.showMessageDialog(project, "上传成功!", "(*^_^*)", null);
                                BrowserUtil.browse("https://www.pgyer.com/" + pgyResponse.getData().getBuildShortcutUrl(), project);
                            });
                        } else {
                            ApplicationManager.getApplication().invokeLater(() -> Messages.showErrorDialog(pgyResponse.getMessage(), "上传失败！"));
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        if (e.toString().contains("closed")) {
                            //如果是主动取消的情况下
                            ApplicationManager.getApplication().invokeLater(() -> {
                                Messages.showMessageDialog(project, "Upload Canceled", "(*^_^*)", null);
                            });
                        } else {
                            ApplicationManager.getApplication().invokeLater(() -> Messages.showErrorDialog(e.getMessage(), "上传失败"));
                        }
                    }
                }
            });
        } else {
            Messages.showErrorDialog("Plase chose the Apk file need to upload", "Error Tips");
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        super.update(e);
        PsiFile currentEditorFile = e.getData(PlatformDataKeys.PSI_FILE);
        if (currentEditorFile != null) {
            String currentEditorFileName = currentEditorFile.getName();
            String fileName = currentEditorFileName;
            if (currentEditorFileName.endsWith(".apk")) {
                e.getPresentation().setEnabled(true);
            } else {
                e.getPresentation().setEnabled(false);
            }
        } else {
            e.getPresentation().setEnabled(false);
        }
    }


    public static RequestBody createCustomRequestBody(final MediaType contentType, final File file, final ProgressListener listener) {
        return new RequestBody() {
            @Override
            public MediaType contentType() {
                return contentType;
            }

            @Override
            public long contentLength() {
                return file.length();
            }

            @Override
            public void writeTo(BufferedSink sink) throws IOException {
                Source source;
                try {
                    source = Okio.source(file);
                    //sink.writeAll(source);
                    Buffer buf = new Buffer();
                    Long remaining = contentLength();
                    for (long readCount; (readCount = source.read(buf, 2048)) != -1; ) {
                        sink.write(buf, readCount);
                        listener.onProgress(contentLength(), remaining -= readCount, remaining == 0);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
    }

    interface ProgressListener {
        void onProgress(long totalBytes, long remainingBytes, boolean done);
    }


    public static String readableFileSize(long size) {
        if (size <= 0) return "0";
        final String[] units = new String[]{"B", "kB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }
}
