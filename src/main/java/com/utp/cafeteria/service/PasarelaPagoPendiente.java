package com.utp.cafeteria.service;

import org.springframework.stereotype.Component;

@Component
public class PasarelaPagoPendiente implements PasarelaPago {
    @Override
    public String procesar(String metodo, String referencia, double monto) {
        return "PENDIENTE";
    }
}
