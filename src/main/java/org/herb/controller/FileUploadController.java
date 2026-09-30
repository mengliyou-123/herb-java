package org.herb.controller;

import org.herb.pojo.Result;
import org.herb.utils.AliOssUtil;
import org.herb.utils.CurrentUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
public class FileUploadController {
    @Autowired
    private StringRedisTemplate redis;

    @PostMapping("/upload")
    public Result<String> upload(MultipartFile file) throws Exception {
        return uploadImage(file, false);
    }

    @PostMapping("/upload/private")
    public Result<String> uploadPrivate(MultipartFile file) throws Exception {
        return uploadImage(file, true);
    }

    private Result<String> uploadImage(MultipartFile file, boolean privateObject) throws Exception {
        if (file == null || file.isEmpty() || file.getSize() > 5L * 1024 * 1024) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "图片必须小于5MB");
        }
        String quotaKey = "upload:daily:" + CurrentUser.id();
        Long uploads = redis.opsForValue().increment(quotaKey);
        if (uploads != null && uploads == 1) redis.expire(quotaKey, 1, TimeUnit.DAYS);
        if (uploads != null && uploads > 30) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "今日上传次数已达上限");
        }

        try (ImageInputStream imageStream = ImageIO.createImageInputStream(file.getInputStream())) {
            if (imageStream == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无效图片");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageStream);
            if (!readers.hasNext()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅支持 JPEG 和 PNG 图片");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(imageStream);
                String format = reader.getFormatName().toLowerCase();
                if (!format.equals("jpeg") && !format.equals("png")) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅支持 JPEG 和 PNG 图片");
                }
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels <= 0 || pixels > 20_000_000) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "图片尺寸过大");
                }
                BufferedImage image = reader.read(0);
                ByteArrayOutputStream cleaned = new ByteArrayOutputStream();
                String outputFormat = format.equals("jpeg") ? "jpg" : "png";
                if (!ImageIO.write(image, outputFormat, cleaned)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "图片格式不受支持");
                }
                String filename = UUID.randomUUID() + "." + outputFormat;
                return Result.success(AliOssUtil.uploadFile(filename,
                        new ByteArrayInputStream(cleaned.toByteArray()), privateObject));
            } finally {
                reader.dispose();
            }
        }
    }
}
