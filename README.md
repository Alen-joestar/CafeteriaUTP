# ☕ Cafetería UTP · Sistema de Pedidos y Ticket Digital

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.5%20%2F%204.x-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Angular](https://img.shields.io/badge/Angular-22-DD0031?logo=angular&logoColor=white)](https://angular.dev/)
[![MySQL](https://img.shields.io/badge/MySQL-8.x%20%2F%20XAMPP-4479A1?logo=mysql&logoColor=white)](https://www.mysql.com/)
[![Swagger](https://img.shields.io/badge/Swagger-OpenAPI%203-85EA2D?logo=swagger&logoColor=black)](http://localhost:8080/swagger-ui.html)
[![JWT](https://img.shields.io/badge/Security-JWT%20Bearer-black?logo=jsonwebtokens)](https://jwt.io/)

Sistema web **Full-Stack** para la gestión de pedidos en línea, caja, cocina, inventario y recaudación diaria de la **Cafetería UTP (Campus Piura)**. Diseñado con una experiencia de usuario orientada a **eliminar las colas físicas**, permitiendo ordenar digitalmente con emisión de boletos y recojo inmediato en barra.

---

## 📌 Tabla de Contenidos

1. [Visión General y Arquitectura](#-visión-general-y-arquitectura)
2. [Requisitos del Sistema](#-requisitos-del-sistema)
3. [Puesta en Marcha Rápida (Paso a Paso)](#-puesta-en-marcha-rápida-paso-a-paso)
4. [Credenciales de Acceso (Usuarios Semilla)](#-credenciales-de-acceso-usuarios-semilla)
5. [Configuración y Variables de Entorno](#-configuración-y-variables-de-entorno)
6. [Estructura del Proyecto](#-estructura-del-proyecto)
7. [Documentación de la API (Swagger UI)](#-documentación-de-la-api-swagger-ui)
8. [Pruebas Automatizadas y Postman](#-pruebas-automatizadas-y-postman)
9. [Resolución de Problemas Frecuentes (FAQ)](#-resolución-de-problemas-frecuentes-faq)

---

## 🏛 Visión General y Arquitectura

El sistema se compone de tres capas principales diseñadas para trabajar desacopladas:

```
┌────────────────────────────────┐       HTTP / JSON (REST + JWT)       ┌──────────────────────────────┐
│        FRONTEND ANGULAR 22     │ ────────────────────────────────────> │      BACKEND SPRING BOOT     │
│  (Signals + Ticket UI + SVG)   │ <──────────────────────────────────── │    (Controllers + Services)  │
│      http://localhost:4200     │                                       │     http://localhost:8080    │
└────────────────────────────────┘                                       └──────────────┬───────────────┘
                                                                                        │
                                                                               JPA / Hibernate (Pool)
                                                                                        │
                                                                                        ▼
                                                                         ┌──────────────────────────────┐
                                                                         │       MYSQL / XAMPP          │
                                                                         │       (cafeteria_db)         │
                                                                         │    localhost:3306 (root)     │
                                                                         └──────────────────────────────┘
```

### Funcionalidades por Rol

*   **👨‍🎓 Estudiante (`ROL: ESTUDIANTE`):**
    *   **Carta del Día:** Catálogo clasificado por categorías (*Sándwiches, Antojitos, Bebidas, Postres*), filtro desplegable y buscador reactivo.
    *   **Ticket Digital (Dock Lateral):** Carrito con formato de recibo perforado, cantidades dinámicas, selector de pago (*Yape, Plin, Tarjeta*), número de referencia y notas especiales para la cocina.
    *   **Seguimiento en Vivo (Stepper de 4 Etapas):**
        $$\text{01 RECIBIDO} \longrightarrow \text{02 EN COCINA} \longrightarrow \text{03 LISTO EN BARRA} \longrightarrow \text{04 ENTREGADO}$$
    *   **Monitor de Barra:** Cápsula superior que anuncia los boletos listos para recoger en tiempo real.

*   **👨‍🍳 Personal de Cafetería (`ROL: CAFETERIA`):**
    *   **Tablero de Comandas:** Gestión de órdenes en cocina, verificación de comprobante de pago y avance de estados en un solo clic.
    *   **Métricas y Recaudación:** 4 KPIs automáticos (ingresos totales por fecha, órdenes nuevas, en preparación y listas).
    *   **Gestión Integral (CRUD):** Creación/edición de productos de la carta con ajuste rápido de stock (`+5 Stock`) y administración de usuarios.

---

## 💻 Requisitos del Sistema

Para clonar y poner a funcionar este proyecto en cualquier PC, asegúrate de tener instalado:

1. **[Git](https://git-scm.com/):** Para clonar el repositorio.
2. **[Java OpenJDK 25](https://jdk.java.net/25/):** Configurado en la variable de entorno `JAVA_HOME`.
   > *Nota:* **No** necesitas instalar Maven; el repositorio ya incluye el ejecutable wrapper `mvnw` / `mvnw.cmd`.
3. **[Node.js](https://nodejs.org/) (v20 LTS o v22 LTS) + npm:** Para el cliente web.
   > *Nota:* **No** necesitas instalar Angular CLI globalmente.
4. **[XAMPP](https://www.apachefriends.org/es/index.html) (o MySQL Server en el puerto 3306):**
   > Solo se requiere encender el módulo de **MySQL**.

---

## 🚀 Puesta en Marcha Rápida (Paso a Paso)

### 1. Clonar el repositorio
Abre una terminal (PowerShell, CMD o Bash) y ejecuta:
```bash
git clone https://github.com/Alen-joestar/CafeteriaUTP.git
cd CafeteriaUTP
```

### 2. Encender MySQL en XAMPP
1. Abre el **XAMPP Control Panel**.
2. Presiona **Start** en el módulo **MySQL** (puerto estándar: `3306`).
> **Importante:** No necesitas crear tablas ni insertar datos a mano. La aplicación se encarga de crear la base `cafeteria_db`, todas las tablas y sembrar los productos y usuarios iniciales de manera 100% automática al arrancar.

### 3. Iniciar el Backend (Terminal 1)
En la carpeta raíz del proyecto (`CafeteriaUTP`):

*   **En Windows (PowerShell / CMD):**
    ```powershell
    .\mvnw.cmd spring-boot:run
    ```
*   **En Linux / macOS:**
    ```bash
    chmod +x mvnw
    ./mvnw spring-boot:run
    ```

El servidor estará listo cuando veas el mensaje:
`Started CafeteriaApplication in ... seconds (process running on port 8080)`.

*   **URL de la API:** `http://localhost:8080`
*   **Documentación Swagger UI:** `http://localhost:8080/swagger-ui.html`

### 4. Iniciar el Frontend (Terminal 2)
Abre una **segunda terminal**, navega a la carpeta `frontend`, instala dependencias e inicia el servidor de Angular:

```bash
cd frontend
npm install
npm start
```

*   **Aplicación Web en el navegador:** `http://localhost:4200`

---

## 🔑 Credenciales de Acceso (Usuarios Semilla)

El sistema crea automáticamente dos cuentas de prueba al iniciar:

| Perfil | Código / Usuario | Correo | Contraseña | Capacidades |
| :--- | :--- | :--- | :--- | :--- |
| **Cafetería (Admin)** | `ADMIN` | `admin@cafeteria.local` | `CafeAdmin2026` | Acceso a Carta, Pedidos y **03 / CAJA & COCINA** (Comandas, KPIs, CRUD). |
| **Estudiante** | `U2303001` | `u2303001@utp.edu.pe` | `CafeAdmin2026` | Realizar pedidos, pagar con Yape/Plin/Tarjeta y dar seguimiento a sus boletos. |

> 💡 **Tip:** En la ventana de inicio de sesión de la web dispones de botones de **Autocompletado Rápido** (`Estudiante` y `Admin`) para acceder con un solo clic. También puedes registrar nuevos estudiantes desde la pestaña **Crear Cuenta**.

---

## ⚙️ Configuración y Variables de Entorno

El archivo `src/main/resources/application.properties` ya está configurado para la instalación por defecto de XAMPP (usuario `root` sin contraseña en el puerto `3306`). Si tu entorno local usa una configuración distinta, puedes sobreescribir los valores mediante variables de entorno en tu terminal:

```powershell
# Ejemplo en PowerShell (Windows) si tu MySQL tiene contraseña:
$env:MYSQL_PASSWORD = "tu_password_aqui"
$env:DB_PORT = "3306"
$env:DB_HOST = "localhost"
.\mvnw.cmd spring-boot:run
```

Variables disponibles:
*   `MYSQL_PASSWORD`: Contraseña del usuario MySQL (por defecto vacía).
*   `DB_USERNAME`: Usuario de base de datos (por defecto `root`).
*   `DB_HOST`: Host del servidor MySQL (por defecto `localhost`).
*   `DB_PORT`: Puerto de conexión (por defecto `3306`).
*   `CAFETERIA_ADMIN_PASSWORD`: Contraseña para el usuario `ADMIN` inicial (por defecto `CafeAdmin2026`).
*   `JWT_SECRET`: Llave secreta HMAC-SHA256 para la firma de tokens.

---

## 📂 Estructura del Proyecto

```text
CafeteriaUTP/
├── src/main/java/com/utp/cafeteria/
│   ├── config/              # Seguridad (CORS, JWT), OpenAPI Swagger y BootstrapData (Semillas)
│   ├── controller/          # Endpoints REST (Auth, Productos, Pedidos, Usuarios)
│   ├── model/               # Entidades JPA (Usuario, Producto, Pedido, ItemPedido) y DTOs
│   ├── repository/          # Repositorios Spring Data JPA
│   ├── security/            # Servicios y filtros de autenticación JWT
│   └── service/             # Lógica de negocio (validación y deducción de stock, recaudación)
├── src/main/resources/
│   └── application.properties # Configuración de conexión y puertos
├── src/test/                # Suite de pruebas unitarias y de integración con H2
├── frontend/                # Aplicación Web Angular 22
│   ├── src/app/
│   │   ├── models/          # Modelos TypeScript
│   │   ├── services/        # ApiService e Interceptor JWT Bearer
│   │   ├── app.ts           # Lógica y estado reactivo con Signals
│   │   ├── app.html         # Plantilla interactiva con diseño de tickets e iconos SVG
│   │   └── app.css          # Estilos del sistema de diseño UTP (Syne + Space Mono)
│   └── package.json
├── data/                    # Colecciones de prueba para Postman y REST Client
├── pom.xml                  # Dependencias Maven y configuración del compilador Java 25
└── README.md
```

---

## 📖 Documentación de la API (Swagger UI)

Una vez iniciado el backend, puedes probar y consultar todos los endpoints de manera interactiva con autorización Bearer Token en:

👉 **[http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)**

### Principales Endpoints REST

| Método | Endpoint | Acceso | Descripción |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/auth/login` | Público | Autentica usuario y retorna Token JWT enriquecido. |
| `POST` | `/api/auth/registro` | Público | Registro público de nuevos estudiantes. |
| `GET` | `/api/auth/me` | Autenticado | Obtiene los datos del usuario autenticado. |
| `GET` | `/api/productos` | Público | Catálogo completo (soporta filtros `?categoria=` y `?disponible=`). |
| `POST/PUT/DELETE` | `/api/productos/**` | `CAFETERIA` | Gestión de productos, imágenes y stock. |
| `GET` | `/api/pedidos` | Autenticado | Historial de pedidos (propios para estudiantes, global para admin). |
| `POST` | `/api/pedidos` | Autenticado | Emite un pedido y descuenta el stock en base de datos. |
| `PATCH` | `/api/pedidos/{id}/estado` | Autenticado | Cambio de estado (`NUEVO`, `EN_PREPARACION`, `LISTO`, `ENTREGADO`). |
| `GET` | `/api/pedidos/recaudacion` | `CAFETERIA` | Total recaudado de pedidos entregados (`?fecha=YYYY-MM-DD`). |
| `CRUD` | `/api/usuarios/**` | `CAFETERIA` | Administración de usuarios y asignación de roles. |

---

## 🧪 Pruebas Automatizadas y Postman

### Ejecutar Pruebas Unitarias (Backend)
Las pruebas unitarias y de integración utilizan una base de datos **H2 en memoria**, por lo que se pueden ejecutar incluso sin tener MySQL encendido:

```powershell
.\mvnw.cmd test
```

### Colección de Postman
En la carpeta [`data/`](file:///D:/Universidad/Gestion%20del%20servicio%20TI/cafeteria/data) se incluye:
*   `Cafeteria API - Seguridad y CRUD.postman_collection.json`: Colección completa de peticiones.
*   `Cafeteria Local.postman_environment.json`: Variables de entorno local.
*   `GUIA_PRUEBAS_POSTMAN.md`: Instrucciones paso a paso para ejecutar las pruebas en Postman.

---

## ❓ Resolución de Problemas Frecuentes (FAQ)

<details>
<summary><b>1. Error: "Communications link failure / Connection refused" al iniciar el Backend</b></summary>
<br>
Asegúrate de que el servicio <b>MySQL</b> en el panel de control de XAMPP esté en color verde (corriendo en el puerto <code>3306</code>).
</details>

<details>
<summary><b>2. Error: "Access denied for user 'root'@'localhost' (using password: YES)"</b></summary>
<br>
Si tu instalación de MySQL tiene una contraseña configurada para el usuario root, especifícala en tu terminal antes de iniciar:
<pre><code>$env:MYSQL_PASSWORD = "tu_password"</code></pre>
</details>

<details>
<summary><b>3. Error: "Port 8080 or 4200 already in use"</b></summary>
<br>
Si otro proceso tiene ocupado el puerto, puedes liberarlo en PowerShell con:
<pre><code>Get-NetTCPConnection -LocalPort 8080, 4200 -ErrorAction SilentlyContinue | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }</code></pre>
</details>

<details>
<summary><b>4. ¿Es necesario ejecutar scripts SQL manualmente?</b></summary>
<br>
<b>No.</b> Gracias a Hibernate (<code>ddl-auto=update</code>) y a la clase <code>BootstrapData</code>, la base de datos se autoconfigura y precarga con los 11 productos y usuarios de prueba la primera vez que se ejecuta el backend.
</details>
