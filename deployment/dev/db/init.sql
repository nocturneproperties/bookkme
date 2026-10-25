-- Creates a dedicated MariaDB user per microservice, each scoped to its own schema only.

CREATE DATABASE IF NOT EXISTS `user`;
CREATE USER IF NOT EXISTS 'user'@'%' IDENTIFIED BY 'vzEp522h4NBO2MHiO8D4MsgGrDlaKZjP';
GRANT ALL PRIVILEGES ON `user`.* TO 'user'@'%';

CREATE DATABASE IF NOT EXISTS `authorization`;
CREATE USER IF NOT EXISTS 'authorization'@'%' IDENTIFIED BY '8LtCo4ZWXh81JQXkvhNNnbsw84PhiqGy';
GRANT ALL PRIVILEGES ON `authorization`.* TO 'authorization'@'%';

CREATE DATABASE IF NOT EXISTS `business`;
CREATE USER IF NOT EXISTS 'business'@'%' IDENTIFIED BY 'YEw3cV3F1NqHlw8jDpX8si75R4pjqKCk';
GRANT ALL PRIVILEGES ON `business`.* TO 'business'@'%';

CREATE DATABASE IF NOT EXISTS `appointments`;
CREATE USER IF NOT EXISTS 'appointments'@'%' IDENTIFIED BY 'eDVR6pBIlqVgidtvjQiUErgr7VeMD5pj';
GRANT ALL PRIVILEGES ON `appointments`.* TO 'appointments'@'%';

CREATE DATABASE IF NOT EXISTS `notifications`;
CREATE USER IF NOT EXISTS 'notifications'@'%' IDENTIFIED BY '94DVwSITc8AUpL7r0ErOYrZ6iFQt4OJh';
GRANT ALL PRIVILEGES ON `notifications`.* TO 'notifications'@'%';

CREATE DATABASE IF NOT EXISTS `keycloak`;
CREATE USER IF NOT EXISTS 'keycloak'@'%' IDENTIFIED BY '4FB3P4y3mbRKAxASQjM6cBA52qKMFDqW';
GRANT ALL PRIVILEGES ON `keycloak`.* TO 'keycloak'@'%';

-- The monolith runs all five services in one process sharing one set of DB
-- credentials (AppLevelConstants reads APPLICATION_DB_USER/PASSWORD process-wide),
-- so it needs a single user with grants on every service schema instead of one
-- schema-scoped user per service.
CREATE USER IF NOT EXISTS 'monolith'@'%' IDENTIFIED BY 'tC8Wg5SthivHMp05ADh3pBbSgXg5z';
GRANT ALL PRIVILEGES ON `user`.* TO 'monolith'@'%';
GRANT ALL PRIVILEGES ON `authorization`.* TO 'monolith'@'%';
GRANT ALL PRIVILEGES ON `business`.* TO 'monolith'@'%';
GRANT ALL PRIVILEGES ON `appointments`.* TO 'monolith'@'%';
GRANT ALL PRIVILEGES ON `notifications`.* TO 'monolith'@'%';

FLUSH PRIVILEGES;
