import packageInfo from '../../package.json';

export const environment = {
  appVersion: packageInfo.version,
  production: true,
  /** URL de l'API Gateway (toutes les requêtes passent par la gateway, port 8222) */
  apiUrl: 'http://localhost:8222'
};
