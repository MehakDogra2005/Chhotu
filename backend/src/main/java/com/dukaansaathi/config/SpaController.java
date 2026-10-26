package com.dukaansaathi.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * The website is a single page; its menu changes the address (/inventory, /settings ...) without asking the server.
 * If someone reloads on one of those addresses, or opens a link to it, the server must still hand out the website.
 */
@Controller
public class SpaController {
    @GetMapping({"/inventory", "/advisor", "/customers", "/settings", "/help", "/conversations"})
    public String page() {
        return "forward:/index.html";
    }
}
