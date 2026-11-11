import { CommonModule } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  ItemCarrito,
  LoginResponse,
  Pedido,
  Producto,
  Usuario
} from './models/cafeteria.models';
import { ApiService } from './services/api.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App implements OnInit {
  private readonly api = inject(ApiService);

  // Navegación
  vistaActiva = signal<'CATALOGO' | 'MIS_PEDIDOS' | 'ADMIN'>('CATALOGO');
  subVistaAdmin = signal<'PEDIDOS' | 'PRODUCTOS' | 'USUARIOS'>('PEDIDOS');

  // Sesión
  sesion = signal<LoginResponse | null>(null);
  esCafeteria = computed(() => this.sesion()?.rol === 'CAFETERIA');
  estaAutenticado = computed(() => !!this.sesion()?.token);

  inicialesUsuario = computed(() => {
    const s = this.sesion();
    if (!s) return 'UT';
    const base = (s.nombreCompleto || s.codigo || 'UT').trim();
    const partes = base.split(/\s+/);
    if (partes.length >= 2) {
      return (partes[0][0] + partes[1][0]).toUpperCase();
    }
    return base.substring(0, 2).toUpperCase();
  });

  // Modal Auth
  mostrarModalAuth = signal(false);
  modoAuth = signal<'LOGIN' | 'REGISTRO'>('LOGIN');
  loginIdentificador = '';
  loginPassword = '';
  regCodigo = '';
  regCorreo = '';
  regNombre = '';
  regPassword = '';

  // Notificaciones Toast
  notificacion = signal<{ tipo: 'exito' | 'error'; texto: string } | null>(null);
  cargando = signal(false);

  // Catálogo y Filtros
  productos = signal<Producto[]>([]);
  categoriaSeleccionada = signal<string>('TODOS');
  busquedaProducto = signal<string>('');
  readonly categorias = ['TODOS', 'SANDWICH', 'ANTOJITOS', 'BEBIDAS', 'ENDULZATE'];

  productosFiltrados = computed(() => {
    const cat = this.categoriaSeleccionada();
    const texto = this.busquedaProducto().trim().toLowerCase();
    return this.productos().filter((p) => {
      const coincideCat = cat === 'TODOS' || p.categoria?.toUpperCase() === cat;
      const coincideTexto =
        !texto ||
        p.nombre.toLowerCase().includes(texto) ||
        (p.descripcion || '').toLowerCase().includes(texto);
      return coincideCat && coincideTexto;
    });
  });

  // Carrito de Compras
  carrito = signal<ItemCarrito[]>([]);
  mostrarCarrito = signal(false);
  metodoPago = 'YAPE';
  referenciaPago = '';
  mensajePedido = '';

  totalItemsCarrito = computed(() =>
    this.carrito().reduce((acc, item) => acc + item.cantidad, 0)
  );

  totalMontoCarrito = computed(() =>
    this.carrito().reduce((acc, item) => acc + item.producto.precio * item.cantidad, 0)
  );

  // Pedidos y Recaudación
  pedidos = signal<Pedido[]>([]);
  filtroEstadoPedido = signal<string>('TODOS');
  fechaRecaudacion = new Date().toISOString().slice(0, 10);
  totalRecaudadoDia = signal<number>(0);

  pedidosListosEnBarra = computed(() =>
    this.pedidos().filter((p) => p.estado === 'LISTO')
  );

  pedidosFiltrados = computed(() => {
    const est = this.filtroEstadoPedido();
    const lista = [...this.pedidos()].sort((a, b) => b.id - a.id);
    if (est === 'TODOS') return lista;
    return lista.filter((p) => p.estado === est);
  });

  resumenEstados = computed(() => {
    const todos = this.pedidos();
    return {
      nuevos: todos.filter((p) => p.estado === 'NUEVO').length,
      preparacion: todos.filter((p) => p.estado === 'EN_PREPARACION').length,
      listos: todos.filter((p) => p.estado === 'LISTO').length,
      entregados: todos.filter((p) => p.estado === 'ENTREGADO').length
    };
  });

  // Admin: Formulario Producto
  mostrarModalProducto = signal(false);
  productoEditandoId = signal<number | null>(null);
  formProducto: Producto = this.nuevoFormProducto();

  // Admin: Usuarios
  usuarios = signal<Usuario[]>([]);
  mostrarModalUsuario = signal(false);
  usuarioEditandoId = signal<number | null>(null);
  formUsuario: Usuario = this.nuevoFormUsuario();

  ngOnInit(): void {
    this.restaurarSesion();
    this.cargarProductos();
    if (this.estaAutenticado()) {
      this.cargarPedidos();
      if (this.esCafeteria()) {
        this.consultarRecaudacion();
      }
    }
  }

  private restaurarSesion(): void {
    const guardada = localStorage.getItem('cafeteria_session');
    if (guardada) {
      try {
        this.sesion.set(JSON.parse(guardada));
      } catch {
        localStorage.removeItem('cafeteria_session');
      }
    }
  }

  mostrarMensaje(tipo: 'exito' | 'error', texto: string): void {
    this.notificacion.set({ tipo, texto });
    setTimeout(() => {
      if (this.notificacion()?.texto === texto) {
        this.notificacion.set(null);
      }
    }, 3500);
  }

  resetearFiltroTodos(): void {
    this.categoriaSeleccionada.set('TODOS');
    this.busquedaProducto.set('');
  }

  cambiarCategoriaSelect(valor: string): void {
    this.categoriaSeleccionada.set(valor);
  }

  esEtapaCompletada(
    estadoActual: string,
    etapa: 'NUEVO' | 'EN_PREPARACION' | 'LISTO' | 'ENTREGADO'
  ): boolean {
    const orden = ['NUEVO', 'EN_PREPARACION', 'LISTO', 'ENTREGADO'];
    const idxActual = orden.indexOf(estadoActual);
    const idxEtapa = orden.indexOf(etapa);
    if (idxActual < 0 || idxEtapa < 0) return false;
    return idxActual > idxEtapa || estadoActual === 'ENTREGADO';
  }

  cambiarVista(vista: 'CATALOGO' | 'MIS_PEDIDOS' | 'ADMIN'): void {
    if ((vista === 'MIS_PEDIDOS' || vista === 'ADMIN') && !this.estaAutenticado()) {
      this.abrirModalLogin();
      return;
    }
    this.vistaActiva.set(vista);
    if (vista === 'CATALOGO') {
      this.cargarProductos();
    } else if (vista === 'MIS_PEDIDOS') {
      this.cargarPedidos();
    } else if (vista === 'ADMIN') {
      this.cargarPedidos();
      this.cargarProductos();
      this.consultarRecaudacion();
      if (this.subVistaAdmin() === 'USUARIOS') {
        this.cargarUsuarios();
      }
    }
  }

  cambiarSubVistaAdmin(sub: 'PEDIDOS' | 'PRODUCTOS' | 'USUARIOS'): void {
    this.subVistaAdmin.set(sub);
    if (sub === 'PEDIDOS') {
      this.cargarPedidos();
      this.consultarRecaudacion();
    } else if (sub === 'PRODUCTOS') {
      this.cargarProductos();
    } else if (sub === 'USUARIOS') {
      this.cargarUsuarios();
    }
  }

  // ================= AUTH =================
  abrirModalLogin(): void {
    this.modoAuth.set('LOGIN');
    this.mostrarModalAuth.set(true);
  }

  autocompletarAdmin(): void {
    this.loginIdentificador = 'ADMIN';
    this.loginPassword = 'CafeAdmin2026';
  }

  iniciarSesion(): void {
    if (!this.loginIdentificador.trim() || !this.loginPassword.trim()) {
      this.mostrarMensaje('error', 'Ingresa tu código/correo y contraseña');
      return;
    }
    this.cargando.set(true);
    this.api.login(this.loginIdentificador.trim(), this.loginPassword).subscribe({
      next: (resp) => {
        localStorage.setItem('cafeteria_session', JSON.stringify(resp));
        this.sesion.set(resp);
        this.mostrarModalAuth.set(false);
        this.loginPassword = '';
        this.cargando.set(false);
        this.mostrarMensaje('exito', `¡Bienvenido(a), ${resp.nombreCompleto || resp.codigo}!`);
        this.cargarPedidos();
        if (resp.rol === 'CAFETERIA') {
          this.consultarRecaudacion();
        }
      },
      error: (err) => {
        this.cargando.set(false);
        this.mostrarMensaje('error', err?.error?.error || 'Credenciales incorrectas');
      }
    });
  }

  registrarEstudiante(): void {
    if (
      !this.regCodigo.trim() ||
      !this.regCorreo.trim() ||
      !this.regNombre.trim() ||
      this.regPassword.length < 8
    ) {
      this.mostrarMensaje(
        'error',
        'Completa todos los campos y usa una contraseña de al menos 8 caracteres'
      );
      return;
    }
    this.cargando.set(true);
    this.api
      .registrarEstudiante({
        codigo: this.regCodigo.trim(),
        correo: this.regCorreo.trim(),
        nombreCompleto: this.regNombre.trim(),
        password: this.regPassword
      })
      .subscribe({
        next: (creado) => {
          this.cargando.set(false);
          this.mostrarMensaje(
            'exito',
            `Cuenta creada para ${creado.codigo}. Iniciando sesión...`
          );
          this.loginIdentificador = creado.codigo;
          this.loginPassword = this.regPassword;
          this.regCodigo = '';
          this.regCorreo = '';
          this.regNombre = '';
          this.regPassword = '';
          this.iniciarSesion();
        },
        error: (err) => {
          this.cargando.set(false);
          this.mostrarMensaje('error', err?.error?.error || 'No se pudo completar el registro');
        }
      });
  }

  cerrarSesion(): void {
    localStorage.removeItem('cafeteria_session');
    this.sesion.set(null);
    this.pedidos.set([]);
    this.vistaActiva.set('CATALOGO');
    this.mostrarMensaje('exito', 'Has cerrado sesión correctamente');
  }

  // ================= PRODUCTOS Y CARRITO =================
  cargarProductos(): void {
    this.api.listarProductos().subscribe({
      next: (lista) => this.productos.set(lista),
      error: () => this.mostrarMensaje('error', 'No se pudo conectar con el servidor backend')
    });
  }

  imagenPorDefecto(categoria: string): string {
    switch (categoria?.toUpperCase()) {
      case 'SANDWICH':
        return 'https://images.unsplash.com/photo-1528735602780-2552fd46c7af?auto=format&fit=crop&w=600&q=80';
      case 'ANTOJITOS':
        return 'https://images.unsplash.com/photo-1541592106381-b31e9677c0e5?auto=format&fit=crop&w=600&q=80';
      case 'BEBIDAS':
        return 'https://images.unsplash.com/photo-1509042239860-f550ce710b93?auto=format&fit=crop&w=600&q=80';
      case 'ENDULZATE':
        return 'https://images.unsplash.com/photo-1551024709-8f23befc6f87?auto=format&fit=crop&w=600&q=80';
      default:
        return 'https://images.unsplash.com/photo-1504674900247-0877df9cc836?auto=format&fit=crop&w=600&q=80';
    }
  }

  agregarAlCarrito(producto: Producto): void {
    if (!producto.disponible || (producto.stock != null && producto.stock <= 0)) {
      this.mostrarMensaje('error', 'Este producto está agotado');
      return;
    }
    const actual = [...this.carrito()];
    const idx = actual.findIndex((i) => i.producto.id === producto.id);
    if (idx >= 0) {
      const nuevaCant = actual[idx].cantidad + 1;
      if (producto.stock != null && nuevaCant > producto.stock) {
        this.mostrarMensaje('error', `Solo quedan ${producto.stock} unidades disponibles`);
        return;
      }
      actual[idx] = { ...actual[idx], cantidad: nuevaCant };
    } else {
      actual.push({ producto, cantidad: 1 });
    }
    this.carrito.set(actual);
    this.mostrarMensaje('exito', `${producto.nombre} añadido al ticket`);
  }

  cambiarCantidadCarrito(productoId: number | undefined, delta: number): void {
    if (!productoId) return;
    const actual = [...this.carrito()];
    const idx = actual.findIndex((i) => i.producto.id === productoId);
    if (idx < 0) return;

    const item = actual[idx];
    const nuevaCant = item.cantidad + delta;
    if (nuevaCant <= 0) {
      actual.splice(idx, 1);
    } else {
      if (item.producto.stock != null && nuevaCant > item.producto.stock) {
        this.mostrarMensaje('error', `Stock máximo disponible: ${item.producto.stock}`);
        return;
      }
      actual[idx] = { ...item, cantidad: nuevaCant };
    }
    this.carrito.set(actual);
  }

  vaciarCarrito(): void {
    this.carrito.set([]);
  }

  confirmarPedido(): void {
    if (!this.estaAutenticado()) {
      this.abrirModalLogin();
      this.mostrarMensaje('error', 'Inicia sesión o regístrate para emitir tu orden');
      return;
    }
    if (this.carrito().length === 0) {
      this.mostrarMensaje('error', 'Agrega al menos un producto al ticket');
      return;
    }

    this.cargando.set(true);
    const dto = {
      clienteId: this.sesion()?.usuarioId,
      metodoPago: this.metodoPago,
      referenciaPago: this.referenciaPago.trim() || undefined,
      mensaje: this.mensajePedido.trim() || undefined,
      items: this.carrito().map((i) => ({
        productoId: i.producto.id!,
        cantidad: i.cantidad
      }))
    };

    this.api.crearPedido(dto).subscribe({
      next: (pedidoCreado) => {
        this.cargando.set(false);
        this.carrito.set([]);
        this.referenciaPago = '';
        this.mensajePedido = '';
        this.mostrarMensaje('exito', `Orden ${pedidoCreado.codigo} emitida con éxito`);
        this.cargarProductos();
        this.vistaActiva.set('MIS_PEDIDOS');
        this.cargarPedidos();
      },
      error: (err) => {
        this.cargando.set(false);
        this.mostrarMensaje('error', err?.error?.error || 'Error al procesar el pedido');
      }
    });
  }

  // ================= PEDIDOS =================
  cargarPedidos(): void {
    if (!this.estaAutenticado()) return;
    this.api.listarPedidos().subscribe({
      next: (lista) => this.pedidos.set(lista),
      error: (err) =>
        this.mostrarMensaje('error', err?.error?.error || 'No se pudieron cargar los pedidos')
    });
  }

  actualizarEstadoPedido(
    pedido: Pedido,
    cambios: { estado?: string; estadoPago?: string }
  ): void {
    this.api.cambiarEstadoPedido(pedido.id, cambios).subscribe({
      next: (actualizado) => {
        this.pedidos.set(
          this.pedidos().map((p) => (p.id === actualizado.id ? actualizado : p))
        );
        this.mostrarMensaje('exito', `Orden ${actualizado.codigo} actualizada`);
        this.cargarProductos();
        if (this.esCafeteria()) {
          this.consultarRecaudacion();
        }
      },
      error: (err) => {
        this.mostrarMensaje('error', err?.error?.error || 'No se pudo actualizar el pedido');
      }
    });
  }

  eliminarPedidoAdmin(pedido: Pedido): void {
    this.api.eliminarPedido(pedido.id).subscribe({
      next: () => {
        this.pedidos.set(this.pedidos().filter((p) => p.id !== pedido.id));
        this.mostrarMensaje('exito', `Pedido ${pedido.codigo} eliminado`);
        this.consultarRecaudacion();
      },
      error: (err) =>
        this.mostrarMensaje('error', err?.error?.error || 'No se pudo eliminar el pedido')
    });
  }

  consultarRecaudacion(): void {
    if (!this.esCafeteria()) return;
    this.api.obtenerRecaudacion(this.fechaRecaudacion).subscribe({
      next: (res) => this.totalRecaudadoDia.set(res.totalRecaudado || 0),
      error: () => this.totalRecaudadoDia.set(0)
    });
  }

  // ================= ADMIN: CRUD PRODUCTOS =================
  private nuevoFormProducto(): Producto {
    return {
      nombre: '',
      descripcion: '',
      precio: 4.0,
      categoria: 'SANDWICH',
      disponible: true,
      imagenUrl: '',
      stock: 25
    };
  }

  abrirModalNuevoProducto(): void {
    this.productoEditandoId.set(null);
    this.formProducto = this.nuevoFormProducto();
    this.mostrarModalProducto.set(true);
  }

  abrirModalEditarProducto(p: Producto): void {
    this.productoEditandoId.set(p.id ?? null);
    this.formProducto = { ...p };
    this.mostrarModalProducto.set(true);
  }

  sumarStockRapido(p: Producto, cantidad: number): void {
    if (!p.id) return;
    const nuevoStock = (p.stock ?? 0) + cantidad;
    const actualizado: Producto = {
      ...p,
      stock: nuevoStock,
      disponible: nuevoStock > 0 ? true : p.disponible
    };
    this.api.actualizarProducto(p.id, actualizado).subscribe({
      next: () => {
        this.cargarProductos();
        this.mostrarMensaje('exito', `Stock de ${p.nombre} actualizado a ${nuevoStock}`);
      },
      error: (err) =>
        this.mostrarMensaje('error', err?.error?.error || 'No se pudo actualizar el stock')
    });
  }

  guardarProductoAdmin(): void {
    const id = this.productoEditandoId();
    const obs = id
      ? this.api.actualizarProducto(id, this.formProducto)
      : this.api.crearProducto(this.formProducto);

    obs.subscribe({
      next: () => {
        this.mostrarModalProducto.set(false);
        this.cargarProductos();
        this.mostrarMensaje(
          'exito',
          id ? 'Plato actualizado correctamente' : 'Plato agregado a la carta'
        );
      },
      error: (err) =>
        this.mostrarMensaje('error', err?.error?.error || 'Error al guardar el producto')
    });
  }

  eliminarProductoAdmin(p: Producto): void {
    if (!p.id) return;
    this.api.eliminarProducto(p.id).subscribe({
      next: () => {
        this.cargarProductos();
        this.mostrarMensaje('exito', `Producto "${p.nombre}" eliminado`);
      },
      error: (err) =>
        this.mostrarMensaje(
          'error',
          err?.error?.error || 'No se puede eliminar un producto asociado a pedidos'
        )
    });
  }

  // ================= ADMIN: CRUD USUARIOS =================
  private nuevoFormUsuario(): Usuario {
    return {
      codigo: '',
      correo: '',
      nombreCompleto: '',
      password: '',
      rol: 'ESTUDIANTE'
    };
  }

  cargarUsuarios(): void {
    if (!this.esCafeteria()) return;
    this.api.listarUsuarios().subscribe({
      next: (lista) => this.usuarios.set(lista),
      error: (err) =>
        this.mostrarMensaje('error', err?.error?.error || 'No se pudieron cargar los usuarios')
    });
  }

  abrirModalNuevoUsuario(): void {
    this.usuarioEditandoId.set(null);
    this.formUsuario = this.nuevoFormUsuario();
    this.mostrarModalUsuario.set(true);
  }

  abrirModalEditarUsuario(u: Usuario): void {
    this.usuarioEditandoId.set(u.id ?? null);
    this.formUsuario = { ...u, password: '' };
    this.mostrarModalUsuario.set(true);
  }

  guardarUsuarioAdmin(): void {
    const id = this.usuarioEditandoId();
    const obs = id
      ? this.api.actualizarUsuario(id, this.formUsuario)
      : this.api.crearUsuario(this.formUsuario);

    obs.subscribe({
      next: () => {
        this.mostrarModalUsuario.set(false);
        this.cargarUsuarios();
        this.mostrarMensaje(
          'exito',
          id ? 'Usuario actualizado correctamente' : 'Usuario registrado correctamente'
        );
      },
      error: (err) =>
        this.mostrarMensaje('error', err?.error?.error || 'Error al guardar el usuario')
    });
  }

  eliminarUsuarioAdmin(u: Usuario): void {
    if (!u.id) return;
    this.api.eliminarUsuario(u.id).subscribe({
      next: () => {
        this.cargarUsuarios();
        this.mostrarMensaje('exito', `Usuario ${u.codigo} eliminado`);
      },
      error: (err) =>
        this.mostrarMensaje(
          'error',
          err?.error?.error || 'No se puede eliminar un usuario con pedidos registrados'
        )
    });
  }
}
