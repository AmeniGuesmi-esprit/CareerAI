export type Role = 'ADMIN' | 'USER';

/** Correspond à UserResponse du user-service */
export interface User {
  id: number;
  nom: string;
  prenom: string;
  email: string;
  telephone: string;
  adresse: string;
  role: Role;
  emailVerified: boolean;
  enabled: boolean;
  createdAt?: string;
}

/** Correspond à AuthResponse du user-service */
export interface AuthResponse {
  token: string;
  user: User;
}

export interface RegisterRequest {
  nom: string;
  prenom: string;
  email: string;
  telephone: string;
  adresse: string;
  motDePasse: string;
}

/** Création d'un utilisateur par un ADMIN (compte déjà vérifié) */
export interface AdminCreateUserRequest extends RegisterRequest {
  role: Role;
}

/** motDePasse optionnel : seulement pour changer le mot de passe */
export interface UpdateProfileRequest {
  nom: string;
  prenom: string;
  telephone: string;
  adresse: string;
  motDePasse?: string;
}

export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
}
