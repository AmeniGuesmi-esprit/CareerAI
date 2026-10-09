import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AdminCreateUserRequest, ApiResponse, Role, User } from '../models/user.model';

/** Gestion des utilisateurs (endpoints réservés au rôle ADMIN) */
@Injectable({ providedIn: 'root' })
export class AdminService {
  private readonly USERS_API = `${environment.apiUrl}/api/users`;

  constructor(private http: HttpClient) {}

  getAllUsers(): Observable<ApiResponse<User[]>> {
    return this.http.get<ApiResponse<User[]>>(this.USERS_API);
  }

  createUser(request: AdminCreateUserRequest): Observable<ApiResponse<void>> {
    return this.http.post<ApiResponse<void>>(this.USERS_API, request);
  }

  changeRole(id: number, role: Role): Observable<ApiResponse<User>> {
    return this.http.put<ApiResponse<User>>(`${this.USERS_API}/${id}/role`, null, { params: { role } });
  }

  setEnabled(id: number, value: boolean): Observable<ApiResponse<User>> {
    return this.http.put<ApiResponse<User>>(`${this.USERS_API}/${id}/enabled`, null, { params: { value } });
  }

  deleteUser(id: number): Observable<ApiResponse<void>> {
    return this.http.delete<ApiResponse<void>>(`${this.USERS_API}/${id}`);
  }
}
