package com.utp.cafeteria.service;

public interface PasarelaPago {
    String procesar(String metodo, String referencia, double monto);
}
