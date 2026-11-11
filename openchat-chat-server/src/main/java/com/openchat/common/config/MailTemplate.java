package com.openchat.common.config;

import com.openchat.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.Date;
import java.util.Properties;

@Component
@Slf4j
public class MailTemplate {
    @Value("${mail.host}")
    private String host;
    @Value("${mail.protocol}")
    private String protocol;
    @Value("${mail.auth}")
    private String auth;
    @Value("${mail.user}")
    private String username;
    @Value("${mail.password}")
    private String password;

    public void sendMail(String toAddress,String theme,String content){
        Properties pt = new Properties();
        //设置主机名字
        pt.setProperty("mail.host",host);
        //设置端口号
        pt.setProperty("mail.transport.protocol",protocol);
        //设置邮箱的身份认证
        pt.setProperty("mail.auth",auth);
        /*使用JavaMail发送邮件*/
        //1.创建Session对象，加载Properties对象
        Session session = Session.getInstance(pt);
        //2，通过Session对象得到transport对象
        session.setDebug(true);
        try {
            Transport transport = session.getTransport();
            //3.使用邮箱的用户名和密码连接邮件服务器
            transport.connect(host,username,password);
            //4，设置Message邮件对象
            Message message = createSimpleMail(session,toAddress,theme,content);
            //5.发送邮件
            transport.sendMessage(message,message.getAllRecipients());
            //关闭
            transport.close();
        } catch (Exception e) {
            log.error("邮件发送失败：{}", e.getMessage());
            throw new BusinessException("邮件发送失败,可能的原因 : {%s}".formatted(e.getMessage()));
        }
    }

    private  Message createSimpleMail(Session session,String toAddress,String theme,String content) throws MessagingException {
        //创建邮件对象
        MimeMessage mimeMessage = new MimeMessage(session);
        //设置邮件的发送人
        mimeMessage.setFrom(new InternetAddress(username));
        //设置邮箱的接收人
        mimeMessage.setRecipient(Message.RecipientType.TO,new InternetAddress(toAddress));
        //设置主题
        mimeMessage.setSubject(theme);
        //设置日期
        mimeMessage.setSentDate(new Date());
        //设置文本内容
        mimeMessage.setText(content);
        return mimeMessage;
    }
}
