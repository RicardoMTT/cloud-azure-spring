IF NOT EXISTS (SELECT name FROM sys.databases WHERE name = 'usuariodb')
BEGIN
    CREATE DATABASE usuariodb;
END