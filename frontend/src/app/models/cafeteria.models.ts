export interface Producto {
  id?: number;
  nombre: string;
  descripcion: string;
  precio: number;
  categoria: string;
  disponible: boolean;
  imagenUrl?: string;
  stock?: number | null;
}

export interface Usuario {
  id?: number;
  codigo: string;
  correo: string;
  nombreCompleto: string;
  password?: string;
  rol: 'ESTUDIANTE' | 'CAFETERIA' | string;
}

export interface LoginResponse {
  token: string;
  tipo: string;
  usuarioId: number;
  codigo: string;
  correo: string;
  nombreCompleto: string;
  rol: 'ESTUDIANTE' | 'CAFETERIA' | string;
}

export interface DetallePedido {
  id?: number;
  productoId: number;
  nombreProducto: string;
  cantidad: number;
  precioUnitario: number;
  subtotal: number;
}

export interface Pedido {
  id: number;
  codigo: string;
  clienteId: number;
  nombreCliente?: string;
  codigoCliente?: string;
  mensaje?: string;
  total: number;
  estado: 'NUEVO' | 'EN_PREPARACION' | 'LISTO' | 'ENTREGADO' | 'CANCELADO' | string;
  fechaRegistro: string;
  metodoPago: 'YAPE' | 'PLIN' | 'TARJETA' | string;
  estadoPago: 'PENDIENTE' | 'PAGADO' | 'RECHAZADO' | string;
  referenciaPago?: string;
  pinEntrega?: string;
  horarioRecojo?: string;
  cremasYExtras?: string;
  montoExtras?: number;
  motivoCancelacion?: string;
  calificacion?: number;
  items: DetallePedido[];
}

export interface SolicitudPedidoDTO {
  clienteId?: number;
  mensaje?: string;
  metodoPago: string;
  referenciaPago?: string;
  estado?: string;
  horarioRecojo?: string;
  cremasYExtras?: string;
  montoExtras?: number;
  items: { productoId: number; cantidad: number }[];
}

export interface ItemCarrito {
  producto: Producto;
  cantidad: number;
}

export interface ArqueoCajaResponse {
  fecha: string;
  totalRecaudado: number;
  totalYape?: number;
  totalPlin?: number;
  totalTarjeta?: number;
  cantidadEntregados?: number;
  promedioCalificacion?: number;
  totalCalificaciones?: number;
  topPlatos?: { nombre: string; cantidad: number; recaudado: number }[];
}
