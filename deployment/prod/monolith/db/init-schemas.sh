for schema in user authorization business appointments notifications; do
  docker_process_sql <<-EOSQL
	CREATE DATABASE IF NOT EXISTS \`${schema}\`;
	GRANT ALL PRIVILEGES ON \`${schema}\`.* TO '${MARIADB_USER}'@'%';
	EOSQL
done
