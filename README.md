# Cafetería UTP API

Backend REST con Spring Boot, Spring Data JPA y MySQL/MariaDB. Está preparado para conectarse al servicio MySQL incluido en XAMPP. Hibernate actualiza las tablas existentes al iniciar (`ddl-auto=update`), sin borrar los datos.

## Conectar con XAMPP

1. Inicia **MySQL** desde el panel de control de XAMPP y confirma el puerto configurado (normalmente `3306`). Apache solo es necesario si quieres abrir phpMyAdmin.
2. (Opcional) Crea la base desde phpMyAdmin o ejecuta:

   ```sql
   CREATE DATABASE cafeteria_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```

   Si no la creas manualmente, la URL JDBC intenta crearla al iniciar. El usuario de MySQL debe tener permiso para crear bases de datos.
3. En PowerShell, configura el usuario y contraseña con que XAMPP permite conectarse a MySQL y arranca la API:

   ```powershell
   $env:DB_HOST = "localhost"
   $env:DB_PORT = "3306"
   $env:DB_NAME = "cafeteria_db"
   $env:DB_USERNAME = "root"
   $env:DB_PASSWORD = ""
   $env:CAFETERIA_ADMIN_PASSWORD = "define-una-clave-segura"
   $env:JWT_SECRET = "define-un-secreto-de-al-menos-32-bytes"
   .\mvnw.cmd spring-boot:run
   ```

   XAMPP suele usar `root` sin contraseña en instalaciones locales; si configuraste una contraseña, asígnala a `DB_PASSWORD`. Los valores de conexión también se pueden cambiar mediante variables de entorno. La primera ejecución crea las tablas y, si la base no tiene usuarios, un administrador `ADMIN` con correo `admin@cafeteria.local`; su contraseña es `CAFETERIA_ADMIN_PASSWORD`.

## Autenticación y permisos

`POST /api/auth/login` acepta `identificador` (código o correo) y `password`. Devuelve un JWT Bearer con expiración de una hora. Envía `Authorization: Bearer <token>` a las rutas protegidas.

- `ESTUDIANTE`: puede crear pedidos y consultar únicamente los propios.
- `CAFETERIA`: administra usuarios y productos, y puede consultar, actualizar y eliminar pedidos.
- `GET /api/productos` y `GET /api/productos/{id}` son públicos.

## Rutas CRUD

- `/api/usuarios`: CRUD, protegido con rol `CAFETERIA`.
- `/api/productos`: CRUD; lecturas públicas y cambios con rol `CAFETERIA`. Filtros opcionales: `categoria`, `disponible`.
- `/api/pedidos`: CRUD autenticado; `estado` y `clienteId` filtran la colección. Los estudiantes quedan limitados a su propio `clienteId`.

Los pedidos reciben `productoId` y `cantidad`; el precio y subtotal se obtienen/calculan desde MySQL. El pago admite `TARJETA`, `YAPE` y `PLIN`. La implementación local usa un puerto `PasarelaPago` con adaptador pendiente: deja el pago como `PENDIENTE` y no realiza cobros reales.

## Persistencia y pruebas

La aplicación usa MySQL/MariaDB y el driver MySQL Connector/J. H2 queda solo como dependencia de pruebas, configurada en `src/test/resources/application.properties`; no se utiliza para ejecutar la API. Ejecuta `./mvnw test` (o `.\mvnw.cmd test` en PowerShell) para correr la suite.

## Recursos de pruebas y respaldo

Los archivos de prueba están en `data/`: importa la colección `Cafeteria API - Seguridad y CRUD.postman_collection.json` y el entorno `Cafeteria Local.postman_environment.json` en Postman; la guía detallada está en `data/GUIA_PRUEBAS_POSTMAN.md`. Las peticiones para REST Client están en `data/http/`. Los JSON antiguos se conservan en `data/respaldo/` y no se cargan al iniciar la API.


