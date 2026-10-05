package org.herb.utils;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.CannedAccessControlList;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import java.io.InputStream;
import java.util.Date;

public class AliOssUtil {

    // Endpoint以华北2（北京）为例，其它Region请按实际情况填写。（服务器的区域节点）
    private  static  final String ENDPOINT = "https://oss-cn-beijing.aliyuncs.com";
    // 从环境变量中获取访问凭证。运行本代码示例之前，请确保已设置环境变量OSS_ACCESS_KEY_ID和OSS_ACCESS_KEY_SECRET。
    //EnvironmentVariableCredentialsProvider credentialsProvider = CredentialsProviderFactory.newEnvironmentVariableCredentialsProvider();
    // 填写Bucket名称，例如examplebucket。
    private  static  final String BUCKET_NAME = "herbs-system";

    public static String uploadFile(String objectName, InputStream in) {
        return uploadFile(objectName, in, false);
    }

    public static String uploadFile(String objectName, InputStream in, boolean privateObject) {
        String id = org.herb.config.LocalEnvironment.get("OSS_ACCESS_KEY_ID");
        String secret = org.herb.config.LocalEnvironment.get("OSS_ACCESS_KEY_SECRET");
        if (id == null || id.isBlank() || secret == null || secret.isBlank()) {
            throw new IllegalStateException("OSS credentials are not configured");
        }
        OSS client = new OSSClientBuilder().build(ENDPOINT, id, secret);
        try {
            PutObjectRequest request = new PutObjectRequest(BUCKET_NAME, objectName, in);
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(objectName.endsWith(".png") ? "image/png" : "image/jpeg");
            if (privateObject) metadata.setObjectAcl(CannedAccessControlList.Private);
            request.setMetadata(metadata);
            client.putObject(request);
            if (privateObject) {
                Date expires = new Date(System.currentTimeMillis() + 15 * 60 * 1000L);
                return client.generatePresignedUrl(BUCKET_NAME, objectName, expires).toString();
            }
            return "https://" + BUCKET_NAME + "." +
                    ENDPOINT.substring(ENDPOINT.lastIndexOf("/") + 1) + "/" + objectName;
        } finally {
            client.shutdown();
        }
    }
}
