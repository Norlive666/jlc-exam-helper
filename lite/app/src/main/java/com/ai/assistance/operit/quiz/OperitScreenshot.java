package com.ai.assistance.operit.quiz;

import android.graphics.Bitmap;
import android.util.Base64;
import com.ai.assistance.operit.util.ImageOutputFormat;
import com.ai.assistance.operit.util.ImagePoolManager;
import com.ai.assistance.operit.util.ImageRegistrationOptions;

/** Uses Operit's original normalization/resize/encoding pipeline for every model image. */
final class OperitScreenshot {
    static byte[] encode(Bitmap bitmap) {
        ImagePoolManager pool = ImagePoolManager.INSTANCE;
        String id = pool.addImageFromBitmap(bitmap, "image/png",
            new ImageRegistrationOptions(100, ImageOutputFormat.JPEG, 85, true, 1600));
        try {
            ImagePoolManager.ImageData image = pool.getImage(id);
            if (image == null) throw new IllegalStateException("截图压缩失败");
            return Base64.decode(image.getBase64(), Base64.NO_WRAP);
        } finally {
            pool.removeImage(id);
        }
    }
}
