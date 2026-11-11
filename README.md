# Cafetería UTP · Sistema de Pedidos y Ticket Digital

Sistema web **Full-Stack** para la gestión de pedidos en línea, control de barra/cocina, inventario y recaudación de la **Cafetería UTP (Sede Piura)**.

Construido con **Spring Boot 4 (Java 25)**, **Spring Security + JWT**, **Spring Data JPA (MySQL/MariaDB)**, documentación interactiva **Swagger / OpenAPI 3** y un frontend moderno en **Angular 22** con estética **Ticket-Pass & Platos Flotantes**.

---

## Tabla de Contenidos

1. [Características Principales](#1-características-principales)
2. [Requisitos Previos (Para ejecutar en cualquier PC)](#2-requisitos-previos-para-ejecutar-en-cualquier-pc)
3. [Guía Rápida de Instalación y Ejecución](#3-guía-rápida-de-instalación-y-ejecución)
4. [Credenciales de Prueba (Semilla Automática)](#4-credenciales-de-prueba-semilla-automática)
5. [Configuración Avanzada y Variables de Entorno](#5-configuración-avanzada-y-variables-de-entorno)
6. [Estructura del Proyecto](#6-estructura-del-proyecto)
7. [Endpoints de la API y Documentación Swagger](#7-endpoints-de-la-api-y-documentación-swagger)
8. [Pruebas Automatizadas y Colección Postman](#8-pruebas-automatizadas-y-colección-postman)

---

## 1. Características Principales

- **Catálogo Interactivo (Carta del Día):** Visualización de platos por categorías (`SANDWICH`, `ANTOJITOS`, `BEBIDAS`, `ENDULZATE`), filtro desplegable, buscador en tiempo real y control de stock en vivo.
- **Ticket de Orden Perforado (Carrito Digital):** Selección de cantidades, elección de método de pago (`YAPE`, `PLIN`, `TARJETA`), número de operación/referencia y notas para cocina.
- **Seguimiento de Pedidos en Tiempo Real:** Boletos digitales con barra de progreso de **4 etapas** (`01 RECIBIDO` → `02 EN COCINA` → `03 LISTO EN BARRA` → `04 ENTREGADO`) y monitor superior en vivo de órdenes listas para recoger.
- **Panel Administrativo de Caja & Cocina (`ROL: CAFETERIA`):**
  - **4 KPIs en tiempo real:** Recaudación del día (filtrable por fecha), pedidos Nuevos, En Cocina y Listos en Barra.
  - **Tablero de Comandas Activas:** Confirmación de pago en un clic y cambio rápido de estado (`Cocina`, `Listo`, `Entregar`, `Cancelar`).
  - **CRUD de Productos y Usuarios:** Creación, edición, eliminación e incremento rápido de stock (`+5 Stock`).
- **Seguridad con JWT:** Autenticación stateless mediante tokens Bearer (`HS256`), contraseñas encriptadas con `BCrypt` y control de acceso por roles (`ESTUDIANTE` y `CAFETERIA`).

---

## 2. Requisitos Previos (Para ejecutar en cualquier PC)

Para clonar y ejecutar el proyecto completo en una computadora local, asegúrate de tener instalado:

1. **Git:** Para clonar el repositorio.
2. **Java JDK 25:** Configurado en el `PATH` y `JAVA_HOME` (definido en `pom.xml`).
   - *No es necesario instalar Maven globalmente*, ya que el repositorio incluye el wrapper `mvnw` / `mvnw.cmd`.
3. **Node.js (v20 LTS o v22 LTS recomendados) + npm:** Para ejecutar el frontend en Angular.
   - *No es necesario instalar Angular CLI globalmente*, ya que `npm start` ejecuta la versión local del proyecto.
4. **XAMPP (o servidor MySQL/MariaDB en el puerto `3306`):**
   - Por defecto el proyecto se conecta a `localhost:3306` con el usuario `root` sin contraseña (configuración predeterminada de XAMPP).

---

## 3. Guía Rápida de Instalación y Ejecución

Sigue estos **4 pasos** para levantar todo el entorno en tu PC:

### Paso 1: Clonar el repositorio
```powershell
git clone https://github.com/Alen-joestar/CafeteriaUTP.git
cd CafeteriaUTP
```

### Paso 2: Iniciar MySQL en XAMPP
1. Abre el **XAMPP Control Panel** e inicia el servicio **MySQL** (verifica que esté en el puerto `3306`).
2. **No necesitas crear la base de datos ni las tablas manualmente:** al arrancar el backend por primera vez, Spring Boot y `BootstrapData` crearán automáticamente:
   - La base de datos `cafeteria_db` (`createDatabaseIfNotExist=true`).
   - Todas las tablas (`usuarios`, `productos`, `pedidos`, `pedido_items`).
   - Los **11 productos iniciales** con sus imágenes, precios y stock.
   - Los **2 usuarios de prueba** (`ADMIN` y `U2303001`).

### Paso 3: Levantar el Backend (Terminal 1 - Raíz del proyecto)
En una ventana de PowerShell o terminal ubicada en la raíz del proyecto (`CafeteriaUTP`), ejecuta:

```powershell
.\mvnw.cmd spring-boot:run
```
- **API Backend activa en:** `http://localhost:8080`
- **Swagger UI disponible en:** `http://localhost:8080/swagger-ui.html`

### Paso 4: Levantar el Frontend Angular (Terminal 2)
Abre una **segunda terminal**, entra a la carpeta `frontend`, instala las dependencias (solo la primera vez) e inicia el servidor de desarrollo:

```powershell
cd frontend
npm install
npm start
```
- **Aplicación Web disponible en:** `http://localhost:4200`

---

## 4. Credenciales de Prueba (Semilla Automática)

Al iniciar el sistema, puedes usar cualquiera de estas cuentas (también dispones de botones de **Autocompletado Rápido** dentro del modal de acceso en la web):

| Rol | Código / Usuario | Correo | Contraseña | Permisos |
| :--- | :--- | :--- | :--- | :--- |
| **Cafetería (Admin)** | `ADMIN` | `admin@cafeteria.local` | `CafeAdmin2026` | Acceso total: Carta, Mis Pedidos y Panel `03 / CAJA & COCINA` (Comandas, Recaudación, CRUD Productos y Usuarios). |
| **Estudiante** | `U2303001` | `u2303001@utp.edu.pe` | `CafeAdmin2026` | Emitir órdenes desde la Carta del Día, ver el seguimiento de sus boletos y cancelar pedidos propios en estado `NUEVO`. |

> También puedes registrar nuevas cuentas de estudiante directamente desde la pestaña **"Crear Cuenta Estudiante"** en el modal de inicio de sesión.

---

## 5. Configuración Avanzada y Variables de Entorno

El archivo `src/main/resources/application.properties` ya trae valores por defecto listos para XAMPP local. Si en tu PC el usuario `root` de MySQL tiene contraseña o usas otro puerto, puedes definir estas variables en PowerShell antes de ejecutar `.\mvnw.cmd spring-boot:run`:

```powershell
$env:DB_HOST = "localhost"
$env:DB_PORT = "3306"
$env:DB_NAME = "cafeteria_db"
$env:DB_USERNAME = "root"
$env:MYSQL_PASSWORD = "tu_password_mysql"
$env:CAFETERIA_ADMIN_PASSWORD = "CafeAdmin2026"
$env:JWT_SECRET = "CafeteriaUTP_ClaveSecretaDesarrolloLocal_2026_Segura!"
.\mvnw.cmd spring-boot:run
```

---

## 6. Estructura del Proyecto

```text
CafeteriaUTP/
├── src/
│   ├── main/java/com/utp/cafeteria/
│   │   ├── config/          # SecurityConfig (CORS + JWT), OpenApiConfig, GlobalExceptionHandler, BootstrapData
│   │   ├── controller/      # AuthController, ProductoController, PedidoController, UsuarioController
│   │   ├── model/           # Entidades JPA (Usuario, Producto, Pedido, ItemPedido) y DTOs
│   │   ├── repository/      # Repositorios Spring Data JPA
│   │   ├── security/        # JwtService, JwtAuthenticationFilter, CustomUserDetailsService
│   │   └── service/         # Lógica de negocio, control de stock y pasarela de pago
│   ├── main/resources/
│   │   └── application.properties
│   └── test/                # Pruebas unitarias y de integración con H2 en memoria
├── frontend/                # Aplicación SPA en Angular 22
│   ├── src/app/
│   │   ├── models/          # Interfaces TypeScript (Producto, Pedido, Usuario, LoginResponse)
│   │   ├── services/        # ApiService y AuthInterceptor (inyección automática de JWT Bearer)
│   │   ├── app.ts           # Estado reactivo con Angular Signals
│   │   ├── app.html         # Plantilla con diseño Ticket-Pass & Platos Flotantes e íconos SVG
│   │   └── app.css          # Estilos del sistema de diseño UTP
│   └── package.json
├── data/                    # Colecciones Postman, entorno local y scripts HTTP de prueba
└── pom.xml
```

---

## 7. Endpoints de la API y Documentación Swagger

Con el backend encendido, explora y prueba todos los endpoints desde **Swagger UI**:
- **URL Swagger UI:** `http://localhost:8080/swagger-ui.html`
- **Especificación OpenAPI JSON:** `http://localhost:8080/v3/api-docs`

### Resumen de Rutas Principales

| Método | Ruta | Acceso | Descripción |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/auth/login` | Público | Inicia sesión con código o correo y devuelve el token JWT + datos del usuario. |
| `POST` | `/api/auth/registro` | Público | Registra un nuevo usuario con rol `ESTUDIANTE`. |
| `GET` | `/api/auth/me` | Autenticado | Devuelve los datos del usuario autenticado actualmente. |
| `GET` | `/api/productos` | Público | Lista el catálogo completo (filtros opcionales: `categoria`, `disponible`). |
| `POST / PUT / DELETE` | `/api/productos/**` | `CAFETERIA` | Administración de productos, precios, imágenes y stock. |
| `GET` | `/api/pedidos` | Autenticado | Lista todos los pedidos (`CAFETERIA`) o los pedidos propios (`ESTUDIANTE`). |
| `POST` | `/api/pedidos` | Autenticado | Crea un nuevo pedido validando y descontando el stock automáticamente. |
| `PATCH` | `/api/pedidos/{id}/estado` | Autenticado | Actualiza `estado` y/o `estadoPago` (restaura stock si se cancela). |
| `GET` | `/api/pedidos/recaudacion` | `CAFETERIA` | Calcula el total recaudado de pedidos `ENTREGADO` por fecha (`?fecha=YYYY-MM-DD`). |
| `GET / POST / PUT / DELETE` | `/api/usuarios/**` | `CAFETERIA` | CRUD completo de usuarios del sistema. |

---

## 8. Pruebas Automatizadas y Colección Postman

- **Suite de Pruebas Automatizadas (JUnit 5 + H2 en memoria):**
  No requiere tener MySQL encendido para ejecutarse:
  ```powershell
  .\mvnw.cmd test
  ```
- **Colección Postman y REST Client:**
  En la carpeta `data/` encontrarás la colección `Cafeteria API - Seguridad y CRUD.postman_collection.json`, el archivo de entorno `Cafeteria Local.postman_environment.json` y la guía detallada en `data/GUIA_PRUEBAS_POSTMAN.md`.
