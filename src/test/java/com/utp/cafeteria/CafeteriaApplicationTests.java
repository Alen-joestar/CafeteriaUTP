package com.utp.cafeteria;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;
import com.utp.cafeteria.model.DetallePedido;
import com.utp.cafeteria.model.Pedido;
import com.utp.cafeteria.model.Producto;
import com.utp.cafeteria.model.Usuario;
import com.utp.cafeteria.repository.PedidoRepository;
import com.utp.cafeteria.repository.ProductoRepository;
import com.utp.cafeteria.repository.UsuarioRepository;
import com.utp.cafeteria.service.UsuarioService;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CafeteriaApplicationTests {
    private static final String ADMIN_PASSWORD = "TestAdmin123!";
    private static final String STUDENT_PASSWORD = "StudentPass123!";

    @Autowired MockMvc mockMvc;
    @Autowired PedidoRepository pedidos;
    @Autowired ProductoRepository productos;
    @Autowired UsuarioRepository usuarios;
    @Autowired UsuarioService usuarioService;

    private Usuario admin;
    private Producto producto;
    private String adminToken;

    @BeforeEach
    public void prepararDatos() throws Exception {
        admin = usuarios.buscarPorIdentificador("ADMIN").orElseThrow();
        adminToken = iniciarSesion("ADMIN", ADMIN_PASSWORD);
        producto = productos.saveAndFlush(new Producto(null, "Producto de prueba", "Fixture de integración",
                4.25, "TEST", true));
    }

    @Test
    void rutasPublicasYDenegacionPorDefecto() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isOk());
        mockMvc.perform(get("/api/productos")).andExpect(status().isOk());
        assertDenied(mockMvc.perform(get("/api/usuarios")).andReturn());
        assertDenied(mockMvc.perform(get("/api/pedidos")).andReturn());
        mockMvc.perform(get("/ruta-no-expuesta")).andExpect(status().isForbidden());
    }

    @Test
    void autenticacionRechazaCredencialesYTokensInvalidos() throws Exception {
        MvcResult loginFallido = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"identificador\":\"ADMIN\",\"password\":\"incorrecta\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Credenciales inválidas"))
                .andReturn();
        assertFalse(loginFallido.getResponse().getContentAsString().contains("\"token\""));

        assertDenied(mockMvc.perform(get("/api/usuarios")
                .header("Authorization", "Bearer token-invalido")).andReturn());
    }

    @Test
    void registroPublicoEstudianteLoginEnriquecidoYManjeoErrores400() throws Exception {
        String codigo = "U-REG-" + System.nanoTime();
        String correo = codigo.toLowerCase() + "@utp.edu.pe";
        String bodyRegistro = """
                {"codigo":"%s","correo":"%s","nombreCompleto":"Nuevo Estudiante UTP","password":"Password123!"}
                """.formatted(codigo, correo);

        mockMvc.perform(post("/api/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyRegistro))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigo").value(codigo))
                .andExpect(jsonPath("$.rol").value("ESTUDIANTE"))
                .andExpect(jsonPath("$.password").doesNotExist());

        MvcResult loginOk = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"identificador\":\"" + codigo + "\",\"password\":\"Password123!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andExpect(jsonPath("$.codigo").value(codigo))
                .andExpect(jsonPath("$.correo").value(correo))
                .andExpect(jsonPath("$.nombreCompleto").value("Nuevo Estudiante UTP"))
                .andExpect(jsonPath("$.rol").value("ESTUDIANTE"))
                .andReturn();

        String token = JsonPath.read(loginOk.getResponse().getContentAsString(), "$.token");
        mockMvc.perform(conToken(get("/api/auth/me"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value(codigo))
                .andExpect(jsonPath("$.password").doesNotExist());

        mockMvc.perform(post("/api/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"codigo\":\"\",\"correo\":\"\",\"nombreCompleto\":\"\",\"password\":\"123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void tokenAdminPermiteCrudDeUsuariosSinExponerPassword() throws Exception {
        String codigo = "IT-USER-" + System.nanoTime();
        String correo = codigo.toLowerCase() + "@test.local";
        String body = """
                {"codigo":"%s","correo":"%s","nombreCompleto":"Usuario temporal",
                 "password":"ClaveTemporal123!","rol":"ESTUDIANTE"}
                """.formatted(codigo, correo);

        MvcResult creado = mockMvc.perform(comoAdmin(post("/api/usuarios")
                .contentType(MediaType.APPLICATION_JSON).content(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigo").value(codigo))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn();
        long userId = idDe(creado);

        mockMvc.perform(comoAdmin(get("/api/usuarios/{id}", userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.password").doesNotExist());

        String updated = """
                {"codigo":"%s","correo":"%s","nombreCompleto":"Usuario actualizado","rol":"ESTUDIANTE"}
                """.formatted(codigo, correo);
        mockMvc.perform(comoAdmin(put("/api/usuarios/{id}", userId)
                .contentType(MediaType.APPLICATION_JSON).content(updated)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreCompleto").value("Usuario actualizado"))
                .andExpect(jsonPath("$.password").doesNotExist());

        mockMvc.perform(comoAdmin(delete("/api/usuarios/{id}", userId)))
                .andExpect(status().isNoContent());
    }

    @Test
    void crudDeProductosRespetaPermisosYCatalogoPublico() throws Exception {
        String body = """
                {"nombre":"Bebida temporal","descripcion":"Prueba","precio":3.5,
                 "categoria":"TEST","disponible":true}
                """;
        MvcResult creado = mockMvc.perform(comoAdmin(post("/api/productos")
                .contentType(MediaType.APPLICATION_JSON).content(body)))
                .andExpect(status().isCreated())
                .andReturn();
        long id = idDe(creado);

        mockMvc.perform(get("/api/productos/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Bebida temporal"));

        String update = """
                {"nombre":"Bebida editada","descripcion":"Actualizada","precio":4.5,
                 "categoria":"TEST","disponible":false}
                """;
        mockMvc.perform(comoAdmin(put("/api/productos/{id}", id)
                .contentType(MediaType.APPLICATION_JSON).content(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.precio").value(4.5));

        mockMvc.perform(comoAdmin(delete("/api/productos/{id}", id)))
                .andExpect(status().isNoContent());
    }

    @Test
    void estudianteSoloOperaSobreSusPedidosYNoPuedeUsurparClienteId() throws Exception {
        Usuario estudiante = crearEstudiante();
        String studentToken = iniciarSesion(estudiante.getCodigo(), STUDENT_PASSWORD);

        MvcResult pedidoAjeno = mockMvc.perform(comoAdmin(post("/api/pedidos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(pedido(admin.getId(), producto.getId(), 1))))
                .andExpect(status().isCreated())
                .andReturn();
        long pedidoAjenoId = idDe(pedidoAjeno);

        MvcResult pedidoPropio = mockMvc.perform(conToken(post("/api/pedidos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(pedido(admin.getId(), producto.getId(), 2)), studentToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clienteId").value(estudiante.getId()))
                .andExpect(jsonPath("$.total").value(8.5))
                .andExpect(jsonPath("$.items[0].precioUnitario").value(4.25))
                .andReturn();
        long pedidoPropioId = idDe(pedidoPropio);

        mockMvc.perform(conToken(get("/api/pedidos/{id}", pedidoPropioId), studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clienteId").value(estudiante.getId()));
        mockMvc.perform(conToken(get("/api/pedidos/{id}", pedidoAjenoId), studentToken))
                .andExpect(status().isNotFound());

        MvcResult listado = mockMvc.perform(conToken(get("/api/pedidos?clienteId=" + admin.getId()), studentToken))
                .andExpect(status().isOk())
                .andReturn();
        List<Number> ids = JsonPath.read(listado.getResponse().getContentAsString(), "$[*].id");
        assertTrue(ids.stream().map(Number::longValue).anyMatch(id -> id == pedidoPropioId));
        assertFalse(ids.stream().map(Number::longValue).anyMatch(id -> id == pedidoAjenoId));

        String update = pedido(estudiante.getId(), producto.getId(), 1);
        mockMvc.perform(conToken(put("/api/pedidos/{id}", pedidoPropioId)
                .contentType(MediaType.APPLICATION_JSON).content(update), studentToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(conToken(delete("/api/pedidos/{id}", pedidoPropioId), studentToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(conToken(get("/api/usuarios"), studentToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(conToken(post("/api/productos").contentType(MediaType.APPLICATION_JSON)
                .content("{}"), studentToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(conToken(put("/api/productos/{id}", producto.getId())
                .contentType(MediaType.APPLICATION_JSON).content("{}"), studentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void cafeteriaPuedeActualizarYEliminarPedido() throws Exception {
        MvcResult creado = mockMvc.perform(comoAdmin(post("/api/pedidos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(pedido(admin.getId(), producto.getId(), 1))))
                .andExpect(status().isCreated()).andReturn();
        long id = idDe(creado);

        String update = """
                {"clienteId":%d,"metodoPago":"PLIN","estado":"LISTO",
                 "items":[{"productoId":%d,"cantidad":3}]}
                """.formatted(admin.getId(), producto.getId());
        mockMvc.perform(comoAdmin(put("/api/pedidos/{id}", id)
                .contentType(MediaType.APPLICATION_JSON).content(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("LISTO"))
                .andExpect(jsonPath("$.total").value(12.75));

        mockMvc.perform(comoAdmin(delete("/api/pedidos/{id}", id)))
                .andExpect(status().isNoContent());
    }

    @Test
    void descuentoDeStockCambioDeEstadoAgilYReporteRecaudacion() throws Exception {
        Producto conStock = productos.saveAndFlush(new Producto(null, "Empanada especial", "Horneada",
                5.0, "ANTOJITOS", true, "https://example.com/empanada.jpg", 3));

        MvcResult creado = mockMvc.perform(comoAdmin(post("/api/pedidos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(pedido(admin.getId(), conStock.getId(), 3))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombreCliente").value(admin.getNombreCompleto()))
                .andExpect(jsonPath("$.codigoCliente").value(admin.getCodigo()))
                .andReturn();
        long pedidoId = idDe(creado);

        Producto agotado = productos.findById(conStock.getId()).orElseThrow();
        assertEquals(0, agotado.getStock());
        assertFalse(agotado.getDisponible());

        mockMvc.perform(comoAdmin(patch("/api/pedidos/{id}/estado", pedidoId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"ENTREGADO\",\"estadoPago\":\"PAGADO\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENTREGADO"))
                .andExpect(jsonPath("$.estadoPago").value("PAGADO"));

        mockMvc.perform(comoAdmin(get("/api/pedidos/recaudacion")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fecha").value(LocalDate.now().toString()))
                .andExpect(jsonPath("$.totalRecaudado").value(15.0));
    }

    @Test
    void repositoriosResuelvenIdentificadorFiltrosRelacionesYRecaudacion() {
        assertTrue(usuarios.buscarPorIdentificador(admin.getCodigo()).isPresent());
        assertTrue(usuarios.buscarPorIdentificador(admin.getCorreo()).isPresent());
        assertTrue(productos.buscar("TEST", true).stream().anyMatch(p -> p.getId().equals(producto.getId())));

        DetallePedido detalle = new DetallePedido();
        detalle.setProducto(producto);
        detalle.setCantidad(2);
        detalle.setPrecioUnitario(producto.getPrecio());
        detalle.setSubtotal(producto.getPrecio() * 2);
        Pedido pedido = new Pedido();
        pedido.setCodigo("IT-JPQL-" + System.nanoTime());
        pedido.setCliente(admin);
        pedido.setEstado("ENTREGADO");
        pedido.setFechaRegistro(LocalDate.now() + " 12:00");
        pedido.setMensaje("Prueba de repositorio");
        pedido.setMetodoPago("YAPE");
        pedido.setEstadoPago("PENDIENTE");
        pedido.setTotal(detalle.getSubtotal());
        pedido.setItems(List.of(detalle));
        Pedido guardado = pedidos.saveAndFlush(pedido);

        assertTrue(pedidos.buscar("ENTREGADO", admin.getId()).stream()
                .anyMatch(p -> p.getId().equals(guardado.getId())));
        Pedido cargado = pedidos.buscarPorIdCompleto(guardado.getId()).orElseThrow();
        assertEquals(producto.getNombre(), cargado.getItems().get(0).getNombreProducto());
        assertEquals(detalle.getSubtotal(), pedidos.recaudarPorFecha(LocalDate.now().toString()), 0.001);
    }

    private Usuario crearEstudiante() {
        String codigo = "IT-STUDENT-" + System.nanoTime();
        return usuarioService.registrar(new Usuario(null, codigo, codigo.toLowerCase() + "@test.local",
                "Estudiante de integración", STUDENT_PASSWORD, "ESTUDIANTE"));
    }

    private String iniciarSesion(String identificador, String password) throws Exception {
        String json = "{\"identificador\":\"" + identificador + "\",\"password\":\"" + password + "\"}";
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.token");
    }

    private long idDe(MvcResult result) throws Exception {
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private void assertDenied(MvcResult result) {
        int code = result.getResponse().getStatus();
        assertTrue(code == 401 || code == 403, "Se esperaba 401 o 403, pero se recibió " + code);
    }

    private MockHttpServletRequestBuilder comoAdmin(MockHttpServletRequestBuilder request) {
        return conToken(request, adminToken);
    }

    private MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", "Bearer " + token);
    }

    private String pedido(Long clienteId, Long productoId, int cantidad) {
        return """
                {"clienteId":%d,"metodoPago":"PLIN",
                 "items":[{"productoId":%d,"cantidad":%d}]}
                """.formatted(clienteId, productoId, cantidad);
    }
}
