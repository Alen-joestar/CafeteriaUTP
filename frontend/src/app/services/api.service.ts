import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  LoginResponse,
  Pedido,
  Producto,
  SolicitudPedidoDTO,
  Usuario
} from '../models/cafeteria.models';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = 'http://localhost:8080/api';

  // Auth
  login(identificador: string, password: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.baseUrl}/auth/login`, { identificador, password });
  }

  registrarEstudiante(datos: {
    codigo: string;
    correo: string;
    nombreCompleto: string;
    password: string;
  }): Observable<Usuario> {
    return this.http.post<Usuario>(`${this.baseUrl}/auth/registro`, datos);
  }

  // Productos
  listarProductos(categoria?: string, disponible?: boolean): Observable<Producto[]> {
    let params = new HttpParams();
    if (categoria && categoria !== 'TODOS') {
      params = params.set('categoria', categoria);
    }
    if (disponible !== undefined) {
      params = params.set('disponible', disponible);
    }
    return this.http.get<Producto[]>(`${this.baseUrl}/productos`, { params });
  }

  crearProducto(producto: Producto): Observable<Producto> {
    return this.http.post<Producto>(`${this.baseUrl}/productos`, producto);
  }

  actualizarProducto(id: number, producto: Producto): Observable<Producto> {
    return this.http.put<Producto>(`${this.baseUrl}/productos/${id}`, producto);
  }

  eliminarProducto(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/productos/${id}`);
  }

  // Pedidos
  listarPedidos(estado?: string): Observable<Pedido[]> {
    let params = new HttpParams();
    if (estado && estado !== 'TODOS') {
      params = params.set('estado', estado);
    }
    return this.http.get<Pedido[]>(`${this.baseUrl}/pedidos`, { params });
  }

  crearPedido(dto: SolicitudPedidoDTO): Observable<Pedido> {
    return this.http.post<Pedido>(`${this.baseUrl}/pedidos`, dto);
  }

  cambiarEstadoPedido(
    id: number,
    cambios: { estado?: string; estadoPago?: string }
  ): Observable<Pedido> {
    return this.http.patch<Pedido>(`${this.baseUrl}/pedidos/${id}/estado`, cambios);
  }

  eliminarPedido(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/pedidos/${id}`);
  }

  obtenerRecaudacion(fecha?: string): Observable<{ fecha: string; totalRecaudado: number }> {
    let params = new HttpParams();
    if (fecha) {
      params = params.set('fecha', fecha);
    }
    return this.http.get<{ fecha: string; totalRecaudado: number }>(
      `${this.baseUrl}/pedidos/recaudacion`,
      { params }
    );
  }

  // Usuarios
  listarUsuarios(): Observable<Usuario[]> {
    return this.http.get<Usuario[]>(`${this.baseUrl}/usuarios`);
  }

  crearUsuario(usuario: Usuario): Observable<Usuario> {
    return this.http.post<Usuario>(`${this.baseUrl}/usuarios`, usuario);
  }

  actualizarUsuario(id: number, usuario: Usuario): Observable<Usuario> {
    return this.http.put<Usuario>(`${this.baseUrl}/usuarios/${id}`, usuario);
  }

  eliminarUsuario(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/usuarios/${id}`);
  }
}
