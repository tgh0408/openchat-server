package com.openchat.common.utils;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

public class QRCodeUtils {

    public static String GenerateQRCode(String words, OutputStream outputStream) throws WriterException, IOException {
        //创建一个画者
        MultiFormatWriter mfw = new MultiFormatWriter();
        //内容，类型，高度，宽度，其他信息map类型

        BarcodeFormat type = BarcodeFormat.QR_CODE;
        int width = 1000;
        int height = 1000;
        Map map = new HashMap();
        map.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        map.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L);
        map.put(EncodeHintType.MARGIN, 2);
        //画一个虚拟的二维码对象
        BitMatrix bm = mfw.encode(words, type, width, height, map);
        //流二维码对象的信息写入文件
        int black = Color.BLACK.getRGB();
        int white = Color.WHITE.getRGB();
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                image.setRGB(i, j, bm.get(i,j)?black:white);
            }
        }
        // todo 这边把二维码发送到前端 可以使用输出流
//        File file = new File("C:\\Users\\user\\Desktop\\zzt.jpg");
        Boolean b= ImageIO.write(image, "jpg", outputStream);
        return "";
    }
}
