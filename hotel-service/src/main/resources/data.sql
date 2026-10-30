INSERT INTO hotels (id,name,address) VALUES (1,'Волга','Казань, ул. Баумана, 12'),(2,'Север','Санкт-Петербург, Невский проспект, 30');
INSERT INTO rooms (id,hotel_id,number,available,times_booked,version) VALUES (1,1,'101',true,0,0),(2,1,'102',true,0,0),(3,1,'103',true,0,0),(4,2,'201',true,0,0),(5,2,'202',false,0,0);
ALTER TABLE hotels ALTER COLUMN id RESTART WITH 3;
ALTER TABLE rooms ALTER COLUMN id RESTART WITH 6;
