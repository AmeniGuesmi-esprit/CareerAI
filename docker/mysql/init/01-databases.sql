-- Exécuté une seule fois à la création du volume mysql_data.
-- Les services créent aussi leur base au démarrage (createDatabaseIfNotExist), ce fichier rend la création explicite.
CREATE DATABASE IF NOT EXISTS careerai_users CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS compatibility_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS interview_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
