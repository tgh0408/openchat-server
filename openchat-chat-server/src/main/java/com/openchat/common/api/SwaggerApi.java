package com.openchat.common.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SwaggerApi {
    @GetMapping("/api/openchat")
    public  String swagger() {
        return "redirect:/doc.html";
    }
}
