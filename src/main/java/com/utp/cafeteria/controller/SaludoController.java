package com.utp.cafeteria.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SaludoController {

    @GetMapping("/")
    public String saludo() {
        return "Cafeteria UTP en marcha - API REST Activa";
    }
}