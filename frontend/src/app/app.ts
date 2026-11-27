import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import * as QRCode from 'qrcode';
import {
  ArqueoCajaResponse,
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
export class App implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);
  private pollingTimer: ReturnType<typeof setInterval> | null = null;
  private estadosPreviosPedidos = new Map<number, string>();
  private qrCache = new Map<string, { size: number; cells: boolean[] }>();

  // Navegación
  vistaActiva = signal<'CATALOGO' | 'MIS_PEDIDOS' | 'ADMIN'>('CATALOGO');
  subVistaAdmin = signal<'PEDIDOS' | 'PRODUCTOS' | 'USUARIOS' | 'INCIDENCIAS'>('PEDIDOS');

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

  // Modal Auth y Cambio de Contraseña Propia
  mostrarModalAuth = signal(false);
  mostrarAccesoRapido = signal(false);
  modoAuth = signal<'LOGIN' | 'REGISTRO'>('LOGIN');
  loginIdentificador = '';
  loginPassword = '';
  regCodigo = '';
  regCorreo = '';
  regNombre = '';
  regPassword = '';
  mostrarModalPassword = signal(false);
  passActual = '';
  passNueva = '';

  // Notificaciones Toast, Audio en Tiempo Real y Estado Operativo de Cocina
  notificacion = signal<{ tipo: 'exito' | 'error'; texto: string } | null>(null);
  cargando = signal(false);
  alertasSonorasActivas = signal(true);
  cocinaAbierta = signal(true);
  motivoPausaCocina = signal('Pausa operativa por cambio de turno');

  // Catálogo, Filtros y Platos Favoritos
  productos = signal<Producto[]>([]);
  favoritosIds = signal<number[]>([]);
  categoriaSeleccionada = signal<string>('TODOS');
  busquedaProducto = signal<string>('');
  readonly categorias = ['TODOS', 'FAVORITOS', 'SANDWICH', 'ANTOJITOS', 'BEBIDAS', 'ENDULZATE'];

  productosFiltrados = computed(() => {
    const cat = this.categoriaSeleccionada();
    const texto = this.busquedaProducto().trim().toLowerCase();
    const favs = this.favoritosIds();
    return this.productos().filter((p) => {
      const coincideCat =
        cat === 'TODOS' ||
        (cat === 'FAVORITOS' ? favs.includes(p.id ?? -1) : p.categoria?.toUpperCase() === cat);
      const coincideTexto =
        !texto ||
        p.nombre.toLowerCase().includes(texto) ||
        (p.descripcion || '').toLowerCase().includes(texto);
      return coincideCat && coincideTexto;
    });
  });

  // Carrito de Compras + Cremas, Extras Dinámicos por Categoría y Horario Personalizable
  carrito = signal<ItemCarrito[]>([]);
  mostrarCarrito = signal(false);
  metodoPago = 'YAPE';
  referenciaPago = '';
  mensajePedido = '';
  modoHorarioRecojo = signal<'INMEDIATO' | 'PROGRAMADO'>('INMEDIATO');
  horaRecojoPersonalizada = '10:45';
  readonly horasSugeridasUtp = ['09:15', '10:45', '12:30', '15:15', '18:30', '20:15'];

  readonly catalogoCremasPorCategoria: Record<string, string[]> = {
    SALADO: ['Mayonesa', 'Ají de la casa', 'Kétchup', 'Salsa criolla'],
    BEBIDAS: ['Con azúcar', 'Sin azúcar', 'Con estevia', 'Bien caliente'],
    ENDULZATE: ['Con canela en polvo', 'Envase para llevar']
  };
  cremasSeleccionadas = signal<string[]>([]);

  readonly catalogoExtrasPorCategoria: {
    id: string;
    grupo: 'SALADO' | 'BEBIDAS' | 'ENDULZATE';
    nombre: string;
    precio: number;
  }[] = [
    { id: 'QUESO', grupo: 'SALADO', nombre: '+Queso fundido', precio: 1.5 },
    { id: 'HUEVO', grupo: 'SALADO', nombre: '+Huevo frito', precio: 1.5 },
    { id: 'PAPAS', grupo: 'SALADO', nombre: '+Papas al hilo extra', precio: 2.0 },
    { id: 'SHOT_CAFE', grupo: 'BEBIDAS', nombre: '+Shot extra de café', precio: 1.5 },
    { id: 'LECHE_DESL', grupo: 'BEBIDAS', nombre: '+Leche deslactosada', precio: 1.0 },
    { id: 'MANJAR_EXT', grupo: 'ENDULZATE', nombre: '+Manjar blanco extra', precio: 1.0 },
    { id: 'COMBINADO', grupo: 'ENDULZATE', nombre: '+Combinado arroz con leche', precio: 2.0 }
  ];
  extrasSeleccionados = signal<string[]>([]);

  gruposCategoriasEnCarrito = computed(() => {
    const grupos = new Set<'SALADO' | 'BEBIDAS' | 'ENDULZATE'>();
    for (const item of this.carrito()) {
      const cat = (item.producto.categoria || '').toUpperCase();
      if (cat === 'SANDWICH' || cat === 'ANTOJITOS') {
        grupos.add('SALADO');
      } else if (cat === 'BEBIDAS') {
        grupos.add('BEBIDAS');
      } else if (cat === 'ENDULZATE') {
        grupos.add('ENDULZATE');
      }
    }
    return grupos;
  });

  cremasDisponibles = computed(() => {
    const grupos = this.gruposCategoriasEnCarrito();
    const lista: string[] = [];
    if (grupos.has('SALADO')) lista.push(...this.catalogoCremasPorCategoria['SALADO']);
    if (grupos.has('BEBIDAS')) lista.push(...this.catalogoCremasPorCategoria['BEBIDAS']);
    if (grupos.has('ENDULZATE')) lista.push(...this.catalogoCremasPorCategoria['ENDULZATE']);
    return lista;
  });

  extrasDisponibles = computed(() => {
    const grupos = this.gruposCategoriasEnCarrito();
    return this.catalogoExtrasPorCategoria.filter((e) => grupos.has(e.grupo));
  });

  etiquetaCremasCarrito = computed(() => {
    const grupos = this.gruposCategoriasEnCarrito();
    if (grupos.has('SALADO') && (grupos.has('BEBIDAS') || grupos.has('ENDULZATE'))) {
      return 'CREMAS Y PREFERENCIAS (GRATIS):';
    }
    if (grupos.has('SALADO')) return 'CREMAS Y SALSAS (GRATIS):';
    if (grupos.has('BEBIDAS')) return 'ENDULZANTE / TEMPERATURA (GRATIS):';
    return 'PRESENTACIÓN (GRATIS):';
  });

  horarioRecojoFinal = computed(() => {
    if (this.modoHorarioRecojo() === 'INMEDIATO') {
      return 'INMEDIATO';
    }
    return `PROGRAMADO ${this.horaRecojoPersonalizada || '10:45'}`;
  });

  totalItemsCarrito = computed(() =>
    this.carrito().reduce((acc, item) => acc + item.cantidad, 0)
  );

  montoExtrasCarrito = computed(() => {
    if (this.carrito().length === 0) return 0;
    const ids = this.extrasSeleccionados();
    const disponibles = this.extrasDisponibles();
    return disponibles
      .filter((e) => ids.includes(e.id))
      .reduce((acc, e) => acc + e.precio, 0);
  });

  resumenCremasYExtras = computed(() => {
    const partes: string[] = [];
    const cremasValidas = this.cremasSeleccionadas().filter((c) =>
      this.cremasDisponibles().includes(c)
    );
    if (cremasValidas.length > 0) {
      partes.push(`Opciones: ${cremasValidas.join(', ')}`);
    }
    const extrasValidos = this.extrasDisponibles().filter((e) =>
      this.extrasSeleccionados().includes(e.id)
    );
    if (extrasValidos.length > 0) {
      const nombresExtras = extrasValidos.map(
        (e) => `${e.nombre} (S/ ${e.precio.toFixed(2)})`
      );
      partes.push(`Adicionales: ${nombresExtras.join(', ')}`);
    }
    return partes.join(' | ');
  });

  descuentoComboCarrito = computed(() => {
    if (this.carrito().length === 0) return 0;
    const grupos = this.gruposCategoriasEnCarrito();
    if (grupos.has('SALADO') && grupos.has('BEBIDAS') && grupos.has('ENDULZATE')) {
      return 2.0;
    }
    if (grupos.has('SALADO') && grupos.has('BEBIDAS')) {
      return 1.0;
    }
    return 0;
  });

  canjearPuntosUtp = signal(false);

  puntosUtpDisponibles = computed(() => {
    if (this.esCafeteria()) return 0;
    let ganados = 0;
    let gastados = 0;
    for (const p of this.pedidos()) {
      if (p.estado === 'ENTREGADO' && p.total) {
        ganados += Math.floor(p.total);
      }
      if (p.estado !== 'CANCELADO' && p.descuentoPuntos && p.descuentoPuntos > 0) {
        gastados += 100;
      }
    }
    return Math.max(0, ganados - gastados);
  });

  descuentoPuntosCarrito = computed(() => {
    if (this.carrito().length === 0) return 0;
    return this.canjearPuntosUtp() && this.puntosUtpDisponibles() >= 100 ? 2.0 : 0.0;
  });

  sugerenciaComboCarrito = computed(() => {
    if (this.carrito().length === 0) return '';
    const grupos = this.gruposCategoriasEnCarrito();
    if (grupos.has('SALADO') && !grupos.has('BEBIDAS')) {
      return 'Combo UTP: Agrega una Bebida y obtén -S/ 1.00 de descuento';
    }
    if (!grupos.has('SALADO') && grupos.has('BEBIDAS')) {
      return 'Combo UTP: Agrega un Sándwich o Antojito y obtén -S/ 1.00';
    }
    if (grupos.has('SALADO') && grupos.has('BEBIDAS') && !grupos.has('ENDULZATE')) {
      return '¡Combo Dúo activo (-S/ 1.00)! Agrega un Postre para Combo Full (-S/ 2.00)';
    }
    if (grupos.has('SALADO') && grupos.has('BEBIDAS') && grupos.has('ENDULZATE')) {
      return '¡Combo Full Universitario UTP activo (-S/ 2.00)!';
    }
    return '';
  });

  totalMontoCarrito = computed(() => {
    const base = this.carrito().reduce(
      (acc, item) => acc + item.producto.precio * item.cantidad,
      0
    );
    return Math.max(
      0,
      base + this.montoExtrasCarrito() - this.descuentoComboCarrito() - this.descuentoPuntosCarrito()
    );
  });

  // Pasarela de Pago Interactiva "UTP Pay" y Boleta Electrónica SUNAT
  mostrarModalPasarela = signal(false);
  tarjetaNumero = '4557 8801 2390 4412';
  tarjetaTitular = 'ESTUDIANTE UTP';
  tarjetaExp = '08/29';
  tarjetaCvv = '842';
  pedidoBoletaActiva = signal<Pedido | null>(null);
  pedidoQrActiva = signal<Pedido | null>(null);
  boletoVerificadoQr = signal<Pedido | null>(null);
  productoKardexModal = signal<Producto | null>(null);

  resumenKardexProducto = computed(() => {
    const prod = this.productoKardexModal();
    if (!prod) return null;
    let enCurso = 0;
    let entregadas = 0;
    let recaudado = 0;
    let devueltasCanceladas = 0;
    const movimientos: { codigo: string; fecha: string; estado: string; cantidad: number; cliente: string }[] = [];

    for (const ped of this.pedidos()) {
      for (const it of ped.items || []) {
        if (it.productoId === prod.id || it.nombreProducto?.toLowerCase() === prod.nombre.toLowerCase()) {
          if (ped.estado === 'ENTREGADO') {
            entregadas += it.cantidad;
            recaudado += it.subtotal;
          } else if (ped.estado === 'CANCELADO') {
            devueltasCanceladas += it.cantidad;
          } else {
            enCurso += it.cantidad;
          }
          movimientos.push({
            codigo: ped.codigo,
            fecha: ped.fechaRegistro,
            estado: ped.estado,
            cantidad: it.cantidad,
            cliente: ped.nombreCliente || ped.codigoCliente || 'Estudiante'
          });
        }
      }
    }
    return {
      enCurso,
      entregadas,
      recaudado,
      devueltasCanceladas,
      totalMovimientos: movimientos.length,
      movimientos: movimientos.slice(0, 5)
    };
  });

  // Pedidos, Tiempo Estimado Dinámico, Escáner de Barra y Arqueo de Caja
  pedidos = signal<Pedido[]>([]);
  monitorBarraPublico = signal<
    { id: number; codigo: string; nombreCliente: string; codigoCliente: string; horarioRecojo: string }[]
  >([]);
  subVistaMisPedidos = signal<'EN_CURSO' | 'HISTORIAL'>('EN_CURSO');
  filtroEstadoPedido = signal<string>('EN_CURSO');
  busquedaComandaAdmin = signal<string>('');
  pedidoPorCancelar = signal<Pedido | null>(null);
  motivoCancelacionSeleccionado = 'Error al elegir plato';
  readonly motivosCancelacionEstudiante = [
    'Error al elegir plato',
    'Cambio de horario de clases',
    'Cambiaré de método de pago'
  ];
  readonly motivosCancelacionAdmin = [
    'N° de operación Yape/Plin no verificado',
    'Cliente no se presentó en barra',
    'Solicitud del estudiante en caja',
    'Ajuste operativo de cocina'
  ];
  registroPorEliminar = signal<{
    tipo: 'PRODUCTO' | 'USUARIO';
    id: number;
    etiqueta: string;
  } | null>(null);
  fechaRecaudacion = new Date().toISOString().slice(0, 10);
  totalRecaudadoDia = signal<number>(0);
  arqueoDetalle = signal<ArqueoCajaResponse | null>(null);
  inputPinBarra = '';

  pedidosListosEnBarra = computed(() => {
    const propiosListos = this.pedidos().filter((p) => p.estado === 'LISTO');
    if (propiosListos.length > 0) {
      return propiosListos;
    }
    return this.monitorBarraPublico() as unknown as Pedido[];
  });

  pedidosEnCurso = computed(() =>
    [...this.pedidos()]
      .filter(
        (p) =>
          p.estado === 'NUEVO' ||
          p.estado === 'EN_PREPARACION' ||
          p.estado === 'LISTO'
      )
      .sort((a, b) => b.id - a.id)
  );

  pedidosHistorial = computed(() =>
    [...this.pedidos()]
      .filter((p) => p.estado === 'ENTREGADO' || p.estado === 'CANCELADO')
      .sort((a, b) => b.id - a.id)
  );

  misPedidosVisibles = computed(() =>
    this.subVistaMisPedidos() === 'EN_CURSO'
      ? this.pedidosEnCurso()
      : this.pedidosHistorial()
  );

  pedidosFiltrados = computed(() => {
    const est = this.filtroEstadoPedido();
    const q = this.busquedaComandaAdmin().trim().toLowerCase();
    let lista = [...this.pedidos()].sort((a, b) => b.id - a.id);
    if (est === 'EN_CURSO') {
      lista = lista.filter(
        (p) =>
          p.estado === 'NUEVO' ||
          p.estado === 'EN_PREPARACION' ||
          p.estado === 'LISTO'
      );
    } else if (est !== 'TODOS') {
      lista = lista.filter((p) => p.estado === est);
    }
    if (q) {
      lista = lista.filter(
        (p) =>
          (p.codigo || '').toLowerCase().includes(q) ||
          (p.nombreCliente || '').toLowerCase().includes(q) ||
          (p.codigoCliente || '').toLowerCase().includes(q) ||
          (p.pinEntrega || '').toLowerCase().includes(q) ||
          (p.referenciaPago || '').toLowerCase().includes(q)
      );
    }
    return lista;
  });

  resumenEstados = computed(() => {
    const todos = this.pedidos();
    return {
      nuevos: todos.filter((p) => p.estado === 'NUEVO').length,
      preparacion: todos.filter((p) => p.estado === 'EN_PREPARACION').length,
      listos: todos.filter((p) => p.estado === 'LISTO').length,
      entregados: todos.filter((p) => p.estado === 'ENTREGADO').length,
      cancelados: todos.filter((p) => p.estado === 'CANCELADO').length
    };
  });

  tiempoEstimadoMinutos = computed(() => {
    const enCola = this.resumenEstados().nuevos + this.resumenEstados().preparacion;
    return Math.min(25, 5 + enCola * 2);
  });

  // Mesa de Ayuda / Gestión de Incidencias ITIL
  pedidoIncidenciaModal = signal<Pedido | null>(null);
  incidenciaTipoForm = 'DEMORA_EN_BARRA';
  incidenciaDetalleForm = '';
  respuestasIncidenciaAdmin: Record<number, string> = {};

  pedidosConIncidencia = computed(() =>
    [...this.pedidos()]
      .filter((p) => !!p.incidenciaEstado)
      .sort((a, b) => b.id - a.id)
  );

  incidenciasAbiertasCount = computed(
    () => this.pedidos().filter((p) => p.incidenciaEstado === 'ABIERTA').length
  );

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
    this.restaurarFavoritos();
    this.cargarProductos();
    this.cargarMonitorBarra();
    this.cargarEstadoCocina();
    if (this.estaAutenticado()) {
      this.cargarPedidos(true);
      if (this.esCafeteria()) {
        this.consultarRecaudacion();
      }
    }
    this.verificarParametroQrUrl();
    this.iniciarPollingTiempoReal();
  }

  private verificarParametroQrUrl(): void {
    try {
      const params = new URLSearchParams(window.location.search);
      const cod = params.get('verificar');
      const pin = params.get('pin') || undefined;
      if (cod) {
        this.api.verificarBoletoQr(cod, pin).subscribe({
          next: (ped) => {
            this.boletoVerificadoQr.set(ped);
          },
          error: () => {
            this.mostrarMensaje('error', `No se encontró el boleto #${cod} para verificación QR`);
          }
        });
      }
    } catch {
      // ignore
    }
  }

  ngOnDestroy(): void {
    if (this.pollingTimer) {
      clearInterval(this.pollingTimer);
    }
  }

  private iniciarPollingTiempoReal(): void {
    this.pollingTimer = setInterval(() => {
      this.cargarMonitorBarra();
      this.cargarEstadoCocina();
      if (this.estaAutenticado()) {
        this.cargarPedidos(false);
        if (this.esCafeteria() && this.vistaActiva() === 'ADMIN') {
          this.consultarRecaudacion();
        }
      }
    }, 6000);
  }

  cargarEstadoCocina(): void {
    this.api.obtenerEstadoCocina().subscribe({
      next: (est) => {
        this.cocinaAbierta.set(est.cocinaAbierta);
        if (est.motivoPausa) {
          this.motivoPausaCocina.set(est.motivoPausa);
        }
      },
      error: () => {}
    });
  }

  alternarEstadoCocinaAdmin(): void {
    const nuevoEstado = !this.cocinaAbierta();
    this.api
      .cambiarEstadoCocina(nuevoEstado, this.motivoPausaCocina())
      .subscribe({
        next: (res) => {
          this.cocinaAbierta.set(res.cocinaAbierta);
          this.mostrarMensaje(
            'exito',
            res.cocinaAbierta
              ? 'Recepción de pedidos en cocina HABILITADA'
              : 'Recepción de pedidos en cocina EN PAUSA OPERATIVA'
          );
        },
        error: () =>
          this.mostrarMensaje('error', 'No se pudo cambiar el estado de la cocina')
      });
  }

  private restaurarFavoritos(): void {
    const raw = localStorage.getItem('cafeteria_favoritos');
    if (raw) {
      try {
        this.favoritosIds.set(JSON.parse(raw));
      } catch {
        this.favoritosIds.set([]);
      }
    }
  }

  esFavorito(productoId?: number): boolean {
    if (!productoId) return false;
    return this.favoritosIds().includes(productoId);
  }

  toggleFavorito(productoId?: number, event?: Event): void {
    event?.stopPropagation();
    if (!productoId) return;
    const act = this.favoritosIds();
    const actualizado = act.includes(productoId)
      ? act.filter((id) => id !== productoId)
      : [...act, productoId];
    this.favoritosIds.set(actualizado);
    localStorage.setItem('cafeteria_favoritos', JSON.stringify(actualizado));
  }

  cargarMonitorBarra(): void {
    this.api.listarMonitorBarra().subscribe({
      next: (lista) => this.monitorBarraPublico.set(lista),
      error: () => {}
    });
  }

  private restaurarSesion(): void {
    const guardada = localStorage.getItem('cafeteria_session');
    if (guardada) {
      try {
        const parsed: LoginResponse = JSON.parse(guardada);
        if (parsed?.token && this.esTokenVigente(parsed.token)) {
          this.sesion.set(parsed);
          if (parsed.rol === 'CAFETERIA') {
            this.vistaActiva.set('ADMIN');
          } else {
            this.vistaActiva.set('CATALOGO');
          }
        } else {
          localStorage.removeItem('cafeteria_session');
        }
      } catch {
        localStorage.removeItem('cafeteria_session');
      }
    }
  }

  private esTokenVigente(token: string): boolean {
    try {
      const partes = token.split('.');
      if (partes.length !== 3) return false;
      const payload = JSON.parse(atob(partes[1]));
      if (!payload?.exp) return true;
      return payload.exp * 1000 > Date.now() + 5000;
    } catch {
      return false;
    }
  }

  reproducirCampanaSonora(tipo: 'listo' | 'nuevo' = 'listo'): void {
    if (!this.alertasSonorasActivas()) return;
    try {
      const AudioCtx = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
      const ctx = new AudioCtx();
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.type = 'sine';
      const now = ctx.currentTime;
      if (tipo === 'listo') {
        osc.frequency.setValueAtTime(880, now);
        osc.frequency.setValueAtTime(1174.66, now + 0.14);
      } else {
        osc.frequency.setValueAtTime(659.25, now);
        osc.frequency.setValueAtTime(880, now + 0.14);
      }
      gain.gain.setValueAtTime(0.18, now);
      gain.gain.exponentialRampToValueAtTime(0.001, now + 0.45);
      osc.connect(gain);
      gain.connect(ctx.destination);
      osc.start(now);
      osc.stop(now + 0.45);
    } catch {
      // Ignorar si el navegador bloquea audio sin interacción previa
    }
  }

  mostrarMensaje(tipo: 'exito' | 'error', texto: string): void {
    this.notificacion.set({ tipo, texto });
    setTimeout(() => {
      if (this.notificacion()?.texto === texto) {
        this.notificacion.set(null);
      }
    }, 3800);
  }

  resetearFiltroTodos(): void {
    this.categoriaSeleccionada.set('TODOS');
    this.busquedaProducto.set('');
  }

  cambiarCategoriaSelect(valor: string): void {
    this.categoriaSeleccionada.set(valor);
  }

  toggleCrema(crema: string): void {
    const act = this.cremasSeleccionadas();
    if (act.includes(crema)) {
      this.cremasSeleccionadas.set(act.filter((c) => c !== crema));
    } else {
      this.cremasSeleccionadas.set([...act, crema]);
    }
  }

  toggleExtra(extraId: string): void {
    const act = this.extrasSeleccionados();
    if (act.includes(extraId)) {
      this.extrasSeleccionados.set(act.filter((e) => e !== extraId));
    } else {
      this.extrasSeleccionados.set([...act, extraId]);
    }
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

  // Generador ISO/IEC 18004 Real Escaneable con Cámara de Celular (Reed-Solomon)
  obtenerDatosQrReal(contenido: string): { size: number; cells: boolean[] } {
    const clave = contenido || 'CAFETERIA-UTP';
    const enCache = this.qrCache.get(clave);
    if (enCache) {
      return enCache;
    }
    try {
      const qr = QRCode.create(clave, { errorCorrectionLevel: 'M' });
      const size = qr.modules.size;
      const data = qr.modules.data;
      const cells: boolean[] = new Array(size * size);
      for (let i = 0; i < data.length; i++) {
        cells[i] = data[i] === 1;
      }
      const resultado = { size, cells };
      this.qrCache.set(clave, resultado);
      return resultado;
    } catch {
      return { size: 7, cells: new Array(49).fill(true) };
    }
  }

  obtenerQrBoleto(ped: Pedido): { size: number; cells: boolean[] } {
    const itemsResumen = (ped.items || [])
      .map((i) => `${i.cantidad}x ${i.nombreProducto}`)
      .join(', ');
    const fichaOffline = [
      '=== CAFETERIA UTP · SEDE PIURA ===',
      '[OK] BOLETO DIGITAL VERIFICADO',
      `ORDEN : #${ped.codigo}`,
      `PIN   : ${ped.pinEntrega || '2026'}`,
      `ALUMNO: ${ped.nombreCliente || ped.codigoCliente || 'Estudiante UTP'}`,
      `RECOJO: ${ped.horarioRecojo || 'INMEDIATO'}`,
      `PLATOS: ${itemsResumen || 'Orden UTP'}`,
      `PAGO  : ${ped.metodoPago} (${ped.estadoPago})`,
      `TOTAL : S/ ${(ped.total || 0).toFixed(2)}`,
      `ESTADO: ${ped.estado}`
    ].join('\n');
    return this.obtenerDatosQrReal(fichaOffline);
  }

  abrirVistaVerificacionQr(ped: Pedido): void {
    this.pedidoQrActiva.set(null);
    this.boletoVerificadoQr.set(ped);
  }

  calcularSlaComanda(ped: Pedido): { minutos: number; color: string; fondo: string; etiqueta: string; vencido: boolean } {
    let min = 0;
    if (ped.fechaRegistro) {
      const iso = ped.fechaRegistro.replace(' ', 'T');
      const ts = Date.parse(iso);
      if (!isNaN(ts)) {
        min = Math.max(0, Math.floor((Date.now() - ts) / 60000));
      }
    }
    if (min < 5) {
      return {
        minutos: min,
        color: '#0F766E',
        fondo: 'var(--green-soft)',
        etiqueta: `SLA EN TIEMPO · ${min}m`,
        vencido: false
      };
    }
    if (min <= 10) {
      return {
        minutos: min,
        color: '#92400E',
        fondo: 'var(--amber-soft)',
        etiqueta: `SLA POR VENCER · ${min}m`,
        vencido: false
      };
    }
    return {
      minutos: min,
      color: 'var(--utp-red)',
      fondo: 'var(--utp-red-soft)',
      etiqueta: `¡SLA VENCIDO · PRIORIDAD! (${min}m)`,
      vencido: true
    };
  }

  obtenerEtiquetaNutricional(p: Producto): string {
    const n = (p.nombre || '').toLowerCase();
    if (n.includes('pollo al horno')) return '340 kcal · Alto en proteína · Horneado';
    if (n.includes('pan con pollo')) return '360 kcal · Proteína magra · Contiene gluten';
    if (n.includes('hamburguesa')) return '520 kcal · Carne 100% res · Recién hecho';
    if (n.includes('chicharrón') || n.includes('chicharron')) return '580 kcal · Tradicional · Sin conservantes';
    if (n.includes('papa rellena')) return '410 kcal · Relleno casero · Contiene huevo';
    if (n.includes('empanada')) return '310 kcal · Masa horneada · Contiene gluten';
    if (n.includes('alitas')) return '540 kcal · Proteína crocante · Porción completa';
    if (n.includes('café') || n.includes('cafe')) return '15 kcal · 100% Grano pasado · Antioxidante';
    if (n.includes('refresco')) return '90 kcal · Fruta natural · Vitamina C';
    if (n.includes('alfajor')) return '280 kcal · Contiene lácteos y gluten';
    if (n.includes('mazamorra')) return '240 kcal · Maíz morado natural · Sin lácteos';
    return '290 kcal · Preparación fresca del día';
  }

  obtenerQrPasarela(): { size: number; cells: boolean[] } {
    const texto = [
      `UTP PAY - ORDEN DE PAGO ${this.metodoPago}`,
      'COMERCIO: CAFETERIA UTP S.A.C.',
      'RUC: 20462509236',
      `IMPORTE: S/ ${this.totalMontoCarrito().toFixed(2)}`,
      `HORARIO: ${this.horarioRecojoFinal()}`
    ].join('\n');
    return this.obtenerDatosQrReal(texto);
  }

  obtenerQrBoletaSunat(bol: Pedido): { size: number; cells: boolean[] } {
    const texto = `20462509236|03|B001|000${bol.id}|IGV:${this.calcularIgv(bol.total).toFixed(2)}|TOTAL:${bol.total.toFixed(2)}|${bol.fechaRegistro}|${bol.codigoCliente || 'U2303001'}|${bol.codigo}`;
    return this.obtenerDatosQrReal(texto);
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
      this.cargarPedidos(true);
    } else if (vista === 'ADMIN') {
      this.cargarPedidos(true);
      this.cargarProductos();
      this.consultarRecaudacion();
      if (this.subVistaAdmin() === 'USUARIOS') {
        this.cargarUsuarios();
      }
    }
  }

  cambiarSubVistaAdmin(sub: 'PEDIDOS' | 'PRODUCTOS' | 'USUARIOS' | 'INCIDENCIAS'): void {
    this.subVistaAdmin.set(sub);
    if (sub === 'PEDIDOS' || sub === 'INCIDENCIAS') {
      this.cargarPedidos(true);
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

  autocompletarEstudiante(): void {
    this.loginIdentificador = 'U2303001';
    this.loginPassword = 'CafeAdmin2026';
  }

  actualizarCodigoRegistro(codigo: string): void {
    const sinEspacios = codigo.replace(/[^a-zA-Z0-9_-]/g, '').toUpperCase();
    this.regCodigo = sinEspacios;
    const limpio = sinEspacios.toLowerCase();
    this.regCorreo = limpio ? `${limpio}@utp.edu.pe` : '';
  }

  iniciarSesion(): void {
    if (!this.loginIdentificador.trim() || !this.loginPassword.trim()) {
      this.mostrarMensaje('error', 'Ingresa tu código/correo y contraseña');
      return;
    }
    this.cargando.set(true);
    this.api.login(this.loginIdentificador.trim(), this.loginPassword).subscribe({
      next: (resp) => {
        this.completarInicioSesion(resp);
      },
      error: (err) => {
        this.cargando.set(false);
        this.mostrarMensaje('error', err?.error?.error || 'Credenciales incorrectas');
      }
    });
  }

  private completarInicioSesion(resp: LoginResponse): void {
    localStorage.setItem('cafeteria_session', JSON.stringify(resp));
    this.sesion.set(resp);
    this.mostrarModalAuth.set(false);
    this.loginPassword = '';
    this.cargando.set(false);
    this.estadosPreviosPedidos.clear();
    this.cargarProductos();
    this.cargarPedidos(false);
    if (resp.rol === 'CAFETERIA') {
      this.vaciarCarrito();
      this.vistaActiva.set('ADMIN');
      this.consultarRecaudacion();
    } else {
      this.categoriaSeleccionada.set('TODOS');
      this.busquedaProducto.set('');
      this.vistaActiva.set('CATALOGO');
    }
    this.mostrarMensaje('exito', `¡Bienvenido(a), ${resp.nombreCompleto || resp.codigo}!`);
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
    this.estadosPreviosPedidos.clear();
    this.vaciarCarrito();
    this.vistaActiva.set('CATALOGO');
    this.mostrarMensaje('exito', 'Has cerrado sesión correctamente');
  }

  abrirModalCambioPassword(): void {
    this.passActual = '';
    this.passNueva = '';
    this.mostrarModalPassword.set(true);
  }

  guardarCambioPassword(): void {
    if (!this.passActual.trim() || this.passNueva.length < 8) {
      this.mostrarMensaje(
        'error',
        'Ingresa tu contraseña actual y una nueva de al menos 8 caracteres'
      );
      return;
    }
    this.cargando.set(true);
    this.api.cambiarPasswordPropia(this.passActual, this.passNueva).subscribe({
      next: () => {
        this.cargando.set(false);
        this.mostrarModalPassword.set(false);
        this.passActual = '';
        this.passNueva = '';
        this.mostrarMensaje('exito', 'Tu contraseña ha sido actualizada con éxito');
      },
      error: (err) => {
        this.cargando.set(false);
        this.mostrarMensaje('error', err?.error?.error || 'No se pudo actualizar la contraseña');
      }
    });
  }

  // ================= MESA DE AYUDA / INCIDENCIAS ITIL =================
  abrirModalIncidencia(pedido: Pedido): void {
    this.incidenciaTipoForm = pedido.incidenciaTipo || 'DEMORA_EN_BARRA';
    this.incidenciaDetalleForm = pedido.incidenciaDetalle || '';
    this.pedidoIncidenciaModal.set(pedido);
  }

  enviarIncidenciaEstudiante(): void {
    const ped = this.pedidoIncidenciaModal();
    if (!ped) return;
    if (!this.incidenciaDetalleForm.trim()) {
      this.mostrarMensaje('error', 'Describe brevemente el incidente ocurrido con tu orden');
      return;
    }
    this.api
      .reportarIncidencia(ped.id, this.incidenciaTipoForm, this.incidenciaDetalleForm.trim())
      .subscribe({
        next: (actualizado) => {
          this.pedidoIncidenciaModal.set(null);
          this.pedidos.set(
            this.pedidos().map((p) => (p.id === actualizado.id ? actualizado : p))
          );
          this.mostrarMensaje('exito', `Ticket ITIL registrado para el boleto #${ped.codigo}`);
        },
        error: (err) =>
          this.mostrarMensaje('error', err?.error?.error || 'No se pudo registrar la incidencia')
      });
  }

  resolverIncidenciaAdmin(pedido: Pedido): void {
    const resp = (this.respuestasIncidenciaAdmin[pedido.id] || '').trim();
    if (!resp) {
      this.mostrarMensaje('error', 'Escribe la acción correctiva o respuesta antes de resolver');
      return;
    }
    this.api.resolverIncidencia(pedido.id, resp).subscribe({
      next: (actualizado) => {
        this.respuestasIncidenciaAdmin[pedido.id] = '';
        this.pedidos.set(
          this.pedidos().map((p) => (p.id === actualizado.id ? actualizado : p))
        );
        this.mostrarMensaje('exito', `Incidencia del boleto #${pedido.codigo} marcada como RESUELTA`);
      },
      error: (err) =>
        this.mostrarMensaje('error', err?.error?.error || 'No se pudo resolver la incidencia')
    });
  }

  reabastecerStockCriticoAdmin(): void {
    this.api.reabastecerStockCritico().subscribe({
      next: (lista) => {
        this.productos.set(lista);
        this.mostrarMensaje('exito', 'Stock crítico y agotados reabastecidos a 20 unidades');
      },
      error: () =>
        this.mostrarMensaje('error', 'No se pudo reabastecer el stock crítico')
    });
  }

  // ================= PRODUCTOS Y CARRITO =================
  cargarProductos(): void {
    this.api.listarProductos().subscribe({
      next: (lista) => this.productos.set(lista),
      error: () => {
        if (this.productos().length === 0) {
          this.mostrarMensaje('error', 'No se pudo conectar con el servidor backend');
        }
      }
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
    if (this.esCafeteria()) {
      this.abrirModalEditarProducto(producto);
      return;
    }
    if (!producto.disponible || (producto.stock != null && producto.stock <= 0)) {
      this.mostrarMensaje('error', 'Este producto está agotado');
      return;
    }
    const actual = [...this.carrito()];
    const idx = actual.findIndex((i) => i.producto.id === producto.id);
    if (idx >= 0) {
      const nuevaCant = actual[idx].cantidad + 1;
      if (nuevaCant > 10) {
        this.mostrarMensaje('error', 'El máximo permitido por plato en un mismo ticket es de 10 unidades');
        return;
      }
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
      if (nuevaCant > 10) {
        this.mostrarMensaje('error', 'El máximo permitido por plato en un mismo ticket es de 10 unidades');
        return;
      }
      if (item.producto.stock != null && nuevaCant > item.producto.stock) {
        this.mostrarMensaje('error', `Stock máximo disponible: ${item.producto.stock}`);
        return;
      }
      actual[idx] = { ...item, cantidad: nuevaCant };
    }
    this.carrito.set(actual);
    this.sincronizarOpcionesConCarrito();
  }

  private sincronizarOpcionesConCarrito(): void {
    const cremasPermitidas = this.cremasDisponibles();
    this.cremasSeleccionadas.set(
      this.cremasSeleccionadas().filter((c) => cremasPermitidas.includes(c))
    );
    const extrasPermitidos = this.extrasDisponibles().map((e) => e.id);
    this.extrasSeleccionados.set(
      this.extrasSeleccionados().filter((id) => extrasPermitidos.includes(id))
    );
  }

  vaciarCarrito(): void {
    this.carrito.set([]);
    this.cremasSeleccionadas.set([]);
    this.extrasSeleccionados.set([]);
    this.modoHorarioRecojo.set('INMEDIATO');
    this.canjearPuntosUtp.set(false);
  }

  // ================= PASARELA "UTP PAY" Y EMISIÓN DE ORDEN =================
  abrirPasarelaOPedir(): void {
    if (!this.estaAutenticado()) {
      this.abrirModalLogin();
      this.mostrarMensaje('error', 'Inicia sesión o regístrate para emitir tu orden');
      return;
    }
    if (!this.cocinaAbierta()) {
      this.mostrarMensaje(
        'error',
        `Recepción de órdenes en pausa: ${this.motivoPausaCocina()}`
      );
      return;
    }
    if (this.esCafeteria()) {
      this.mostrarMensaje(
        'error',
        'El rol administrativo CAFETERIA gestiona comandas; no emite pedidos como estudiante'
      );
      return;
    }
    if (this.carrito().length === 0) {
      this.mostrarMensaje('error', 'Agrega al menos un producto al ticket');
      return;
    }
    if (this.modoHorarioRecojo() === 'PROGRAMADO') {
      const h = this.horaRecojoPersonalizada;
      if (!h || h < '07:00' || h > '22:00') {
        this.mostrarMensaje(
          'error',
          'Selecciona una hora de recojo dentro del horario de atención UTP (07:00 a 22:00)'
        );
        return;
      }
      const ahora = new Date();
      const minActuales = ahora.getHours() * 60 + ahora.getMinutes();
      const partes = h.split(':');
      const minSeleccionados = parseInt(partes[0], 10) * 60 + parseInt(partes[1] || '0', 10);
      if (minActuales >= 7 * 60 && minActuales <= 21 * 60 + 45) {
        if (minSeleccionados < minActuales + 10) {
          this.mostrarMensaje(
            'error',
            'La hora programada debe ser al menos 10 minutos posterior a la hora actual (o usa Recojo Inmediato)'
          );
          return;
        }
      }
    }
    this.mostrarModalPasarela.set(true);
  }

  procesarPagoInstantaneoUtpPay(): void {
    const sufijo = Math.floor(100000 + Math.random() * 900000);
    this.referenciaPago = `UTPPAY-${this.metodoPago}-${sufijo}`;
    this.confirmarPedido();
  }

  confirmarPedidoConReferenciaManual(): void {
    const ref = this.referenciaPago.trim();
    if (!ref || ref.length < 6) {
      this.mostrarMensaje(
        'error',
        'Ingresa un N° de operación válido de al menos 6 dígitos (o usa el botón de Pago con QR)'
      );
      return;
    }
    this.confirmarPedido();
  }

  confirmarPedido(): void {
    if (!this.estaAutenticado()) {
      this.abrirModalLogin();
      return;
    }
    if (this.esCafeteria()) {
      this.mostrarMensaje('error', 'El administrador no puede realizar pedidos como cliente');
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
      horarioRecojo: this.horarioRecojoFinal(),
      cremasYExtras: this.resumenCremasYExtras() || undefined,
      montoExtras: this.montoExtrasCarrito(),
      descuentoPuntos: this.descuentoPuntosCarrito(),
      items: this.carrito().map((i) => ({
        productoId: i.producto.id!,
        cantidad: i.cantidad
      }))
    };

    this.api.crearPedido(dto).subscribe({
      next: (pedidoCreado) => {
        this.cargando.set(false);
        this.mostrarModalPasarela.set(false);
        this.carrito.set([]);
        this.cremasSeleccionadas.set([]);
        this.extrasSeleccionados.set([]);
        this.referenciaPago = '';
        this.mensajePedido = '';
        this.modoHorarioRecojo.set('INMEDIATO');
        this.canjearPuntosUtp.set(false);
        this.reproducirCampanaSonora('nuevo');
        this.mostrarMensaje(
          'exito',
          `Orden ${pedidoCreado.codigo} emitida (PIN recojo: ${pedidoCreado.pinEntrega || '----'})`
        );
        this.cargarProductos();
        this.subVistaMisPedidos.set('EN_CURSO');
        this.vistaActiva.set('MIS_PEDIDOS');
        this.cargarPedidos(true);
      },
      error: (err) => {
        this.cargando.set(false);
        this.mostrarMensaje('error', err?.error?.error || 'Error al procesar el pedido');
      }
    });
  }

  // ================= BOLETA ELECTRÓNICA (SUNAT MOCK) =================
  abrirBoletaElectronica(pedido: Pedido): void {
    this.pedidoBoletaActiva.set(pedido);
  }

  calcularOpGravada(total: number): number {
    return total / 1.18;
  }

  calcularIgv(total: number): number {
    return total - this.calcularOpGravada(total);
  }

  imprimirBoleta(): void {
    window.print();
  }

  // ================= PEDIDOS, TIEMPO REAL Y ESCÁNER QR/PIN EN BARRA =================
  cargarPedidos(esCargaInicial = false): void {
    if (!this.estaAutenticado()) return;
    this.api.listarPedidos().subscribe({
      next: (lista) => {
        if (!esCargaInicial && this.estadosPreviosPedidos.size > 0) {
          for (const p of lista) {
            const previo = this.estadosPreviosPedidos.get(p.id);
            if (previo && previo !== 'LISTO' && p.estado === 'LISTO') {
              this.reproducirCampanaSonora('listo');
              this.mostrarMensaje(
                'exito',
                `¡CAMPANA EN BARRA! El boleto #${p.codigo} ya está LISTO PARA RECOGER`
              );
            } else if (!previo && this.esCafeteria() && p.estado === 'NUEVO') {
              this.reproducirCampanaSonora('nuevo');
              this.mostrarMensaje('exito', `Nueva comanda recibida en cocina: #${p.codigo}`);
            }
          }
        }
        this.estadosPreviosPedidos.clear();
        for (const p of lista) {
          this.estadosPreviosPedidos.set(p.id, p.estado);
        }
        this.pedidos.set(lista);
      },
      error: (err) => {
        if (err?.status === 401 || err?.status === 403) {
          localStorage.removeItem('cafeteria_session');
          this.sesion.set(null);
          this.pedidos.set([]);
          this.vistaActiva.set('CATALOGO');
          return;
        }
        if (esCargaInicial && this.pedidos().length === 0) {
          this.mostrarMensaje('error', err?.error?.error || 'No se pudieron cargar los pedidos');
        }
      }
    });
  }

  validarEntregaPorPinEnBarra(codigoOPinManual?: string): void {
    const valor = (codigoOPinManual ?? this.inputPinBarra).trim();
    if (!valor) {
      this.mostrarMensaje('error', 'Ingresa el PIN de 4 dígitos o el código #CAF-XXXX a entregar');
      return;
    }
    this.api.validarEntregaPorPinOCodigo(valor).subscribe({
      next: (entregado) => {
        this.inputPinBarra = '';
        this.reproducirCampanaSonora('listo');
        this.mostrarMensaje(
          'exito',
          `Entrega verificada con éxito: Boleto #${entregado.codigo} (${entregado.nombreCliente || 'Cliente'})`
        );
        this.cargarPedidos(true);
        this.consultarRecaudacion();
      },
      error: (err) => {
        this.mostrarMensaje(
          'error',
          err?.error?.error || 'No se encontró un pedido activo con ese PIN o código'
        );
      }
    });
  }

  solicitarCancelacionPedido(pedido: Pedido): void {
    this.motivoCancelacionSeleccionado = this.esCafeteria()
      ? this.motivosCancelacionAdmin[0]
      : this.motivosCancelacionEstudiante[0];
    this.pedidoPorCancelar.set(pedido);
  }

  confirmarCancelacionPedido(): void {
    const ped = this.pedidoPorCancelar();
    if (!ped) return;
    this.pedidoPorCancelar.set(null);
    this.actualizarEstadoPedido(ped, {
      estado: 'CANCELADO',
      motivoCancelacion: this.motivoCancelacionSeleccionado
    });
  }

  rechazarPagoPedidoAdmin(pedido: Pedido): void {
    this.actualizarEstadoPedido(pedido, {
      estadoPago: 'RECHAZADO',
      motivoCancelacion: 'Pago rechazado en caja: N° de operación no verificado'
    });
  }

  repetirPedidoAnterior(pedido: Pedido): void {
    if (this.esCafeteria()) return;
    const catalogo = this.productos();
    const nuevoCarrito: ItemCarrito[] = [];
    let omitidos = 0;

    for (const det of pedido.items || []) {
      const prod = catalogo.find(
        (p) => p.id === det.productoId || p.nombre.toLowerCase() === det.nombreProducto.toLowerCase()
      );
      if (prod && prod.disponible && (prod.stock == null || prod.stock > 0)) {
        const cant = Math.min(10, det.cantidad, prod.stock ?? 10);
        if (cant > 0) {
          nuevoCarrito.push({ producto: prod, cantidad: cant });
        } else {
          omitidos++;
        }
      } else {
        omitidos++;
      }
    }

    if (nuevoCarrito.length === 0) {
      this.mostrarMensaje('error', 'Los platos de esa orden se encuentran agotados actualmente');
      return;
    }

    this.carrito.set(nuevoCarrito);
    this.sincronizarOpcionesConCarrito();
    this.vistaActiva.set('CATALOGO');
    this.mostrarMensaje(
      'exito',
      omitidos > 0
        ? 'Se cargaron al ticket los platos disponibles de tu orden anterior'
        : `Platos del boleto #${pedido.codigo} cargados nuevamente a tu ticket`
    );
  }

  calificarPedidoEntregado(pedido: Pedido, estrellas: number): void {
    this.api.calificarPedido(pedido.id, estrellas).subscribe({
      next: (actualizado) => {
        this.pedidos.set(
          this.pedidos().map((p) => (p.id === actualizado.id ? actualizado : p))
        );
        this.mostrarMensaje('exito', `¡Gracias! Calificaste la orden #${pedido.codigo} con ${estrellas} ★`);
      },
      error: (err) =>
        this.mostrarMensaje('error', err?.error?.error || 'No se pudo registrar la calificación')
    });
  }

  actualizarEstadoPedido(
    pedido: Pedido,
    cambios: { estado?: string; estadoPago?: string; motivoCancelacion?: string }
  ): void {
    this.api.cambiarEstadoPedido(pedido.id, cambios).subscribe({
      next: (actualizado) => {
        if (cambios.estado === 'LISTO') {
          this.reproducirCampanaSonora('listo');
        }
        this.estadosPreviosPedidos.set(actualizado.id, actualizado.estado);
        this.pedidos.set(
          this.pedidos().map((p) => (p.id === actualizado.id ? actualizado : p))
        );
        this.mostrarMensaje('exito', `Orden ${actualizado.codigo} actualizada a ${actualizado.estado}`);
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
      next: (res) => {
        this.totalRecaudadoDia.set(res.totalRecaudado || 0);
        this.arqueoDetalle.set(res);
      },
      error: () => {
        this.totalRecaudadoDia.set(0);
        this.arqueoDetalle.set(null);
      }
    });
  }

  exportarArqueoCsv(): void {
    const arq = this.arqueoDetalle();
    const entregadosHoy = this.pedidos().filter(
      (p) => p.estado === 'ENTREGADO' && (p.fechaRegistro || '').startsWith(this.fechaRecaudacion)
    );
    const canceladosHoy = this.pedidos().filter(
      (p) => p.estado === 'CANCELADO' && (p.fechaRegistro || '').startsWith(this.fechaRecaudacion)
    );
    const lineas: string[] = [
      'REPORTE DE ARQUEO, CIERRE DE CAJA Y AUDITORIA - CAFETERIA UTP SEDE PIURA',
      `Fecha de Arqueo;,${this.fechaRecaudacion}`,
      `Total Recaudado (S/);,${(arq?.totalRecaudado ?? 0).toFixed(2)}`,
      `Ingresos por YAPE (S/);,${(arq?.totalYape ?? 0).toFixed(2)}`,
      `Ingresos por PLIN (S/);,${(arq?.totalPlin ?? 0).toFixed(2)}`,
      `Ingresos por TARJETA (S/);,${(arq?.totalTarjeta ?? 0).toFixed(2)}`,
      `Ordenes Entregadas;,${arq?.cantidadEntregados ?? entregadosHoy.length}`,
      `Ordenes Canceladas (Auditoria Stock);,${canceladosHoy.length}`,
      `Satisfaccion del Servicio TI (CSAT);,${arq?.promedioCalificacion || 0} / 5 (${arq?.totalCalificaciones || 0} votos)`,
      '',
      '1. DETALLE CONTABLE DE COMANDAS ENTREGADAS',
      'Codigo,Fecha,Cliente,Metodo Pago,Referencia,Horario Recojo,Calificacion,Total (S/)'
    ];
    for (const p of entregadosHoy) {
      lineas.push(
        `${p.codigo},${p.fechaRegistro},"${p.nombreCliente || p.codigoCliente || p.clienteId}",${p.metodoPago},${p.referenciaPago || 'MANUAL'},"${p.horarioRecojo || 'INMEDIATO'}","${p.calificacion ? p.calificacion + ' estrellas' : 'Sin calificar'}",${p.total.toFixed(2)}`
      );
    }
    lineas.push(
      '',
      '2. REGISTRO DE AUDITORIA - ORDENES CANCELADAS (STOCK DEVUELTO AL INVENTARIO)',
      'Codigo,Fecha,Cliente,Metodo Pago,Estado Pago,Motivo Cancelacion,Monto Anulado (S/)'
    );
    for (const c of canceladosHoy) {
      lineas.push(
        `${c.codigo},${c.fechaRegistro},"${c.nombreCliente || c.codigoCliente || c.clienteId}",${c.metodoPago},${c.estadoPago},"${c.motivoCancelacion || 'Cancelado'}",${c.total.toFixed(2)}`
      );
    }
    const contenidoCsv = '\uFEFF' + lineas.join('\r\n');
    const blob = new Blob([contenidoCsv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `arqueo_caja_utp_${this.fechaRecaudacion}.csv`;
    a.click();
    URL.revokeObjectURL(url);
    this.mostrarMensaje('exito', `Arqueo exportado: arqueo_caja_utp_${this.fechaRecaudacion}.csv`);
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

  solicitarEliminarProducto(p: Producto): void {
    if (!p.id) return;
    this.registroPorEliminar.set({
      tipo: 'PRODUCTO',
      id: p.id,
      etiqueta: `Plato "${p.nombre}"`
    });
  }

  solicitarEliminarUsuario(u: Usuario): void {
    if (!u.id) return;
    if (u.codigo?.toUpperCase() === 'ADMIN' || u.codigo === this.sesion()?.codigo) {
      this.mostrarMensaje('error', 'No puedes eliminar la cuenta administradora activa');
      return;
    }
    this.registroPorEliminar.set({
      tipo: 'USUARIO',
      id: u.id,
      etiqueta: `Usuario ${u.codigo} (${u.nombreCompleto})`
    });
  }

  confirmarEliminacionRegistro(): void {
    const reg = this.registroPorEliminar();
    if (!reg) return;
    this.registroPorEliminar.set(null);
    if (reg.tipo === 'PRODUCTO') {
      this.api.eliminarProducto(reg.id).subscribe({
        next: () => {
          this.cargarProductos();
          this.mostrarMensaje('exito', `${reg.etiqueta} eliminado`);
        },
        error: (err) =>
          this.mostrarMensaje(
            'error',
            err?.error?.error || 'No se puede eliminar un producto asociado a pedidos'
          )
      });
    } else {
      this.api.eliminarUsuario(reg.id).subscribe({
        next: () => {
          this.cargarUsuarios();
          this.mostrarMensaje('exito', `${reg.etiqueta} eliminado`);
        },
        error: (err) =>
          this.mostrarMensaje(
            'error',
            err?.error?.error || 'No se puede eliminar un usuario con pedidos registrados'
          )
      });
    }
  }

  eliminarProductoAdmin(p: Producto): void {
    this.solicitarEliminarProducto(p);
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
    this.solicitarEliminarUsuario(u);
  }
}
