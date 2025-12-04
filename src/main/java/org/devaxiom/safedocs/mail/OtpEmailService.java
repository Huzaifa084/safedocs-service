package org.devaxiom.safedocs.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class OtpEmailService {

    private final EmailService emailService;
    private final Map<String, String> otpCache = new ConcurrentHashMap<>();

    public OtpEmailService(EmailService emailService) {
        this.emailService = emailService;
    }

    public String generateOtp() {
        String otp = String.valueOf((int) (Math.random() * 9000) + 1000);
        log.debug("Generated OTP: {}", otp);
        return otp;
    }

    public void sendOtpEmail(String email, String otp) {
        String subject = "OTP for account verification";
        String mailText = "Your OTP is: " + otp + ". Please use this to verify your account.";
        log.info("Sending OTP email to: {}", email);
        emailService.sendEmail(email, subject, mailText);
    }

    public void sendPasswordResetOtp(String email, String otp) {
        String subject = "Password Reset OTP";
        String message = "Your OTP for password reset is: " + otp + ". Please use this to reset your password.";
        log.info("Sending password reset OTP to: {}", email);
        emailService.sendEmail(email, subject, message);
    }

    public void sendAndStoreOtp(String email) {
        String otp = generateOtp();
        otpCache.put(email, otp);
        sendOtpEmail(email, otp);
    }

    // TODO: Not For Production
    public boolean verifyOtp(String email, String otp) {
        if ("1234".equals(otp)) {
            otpCache.remove(email);
            return true;
        }
        String storedOtp = otpCache.get(email);
        if (storedOtp != null && storedOtp.equals(otp)) {
            otpCache.remove(email);
            return true;
        }
        return false;
    }

    // TODO: For Production
//    public boolean verifyOtp(String email, String otp) {
//        String storedOtp = otpCache.get(email);
//        if (storedOtp != null && storedOtp.equals(otp)) {
//            otpCache.remove(email); // remove after successful verification
//            return true;
//        }
//        return false;
//    }
}
