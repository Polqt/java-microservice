alter table auctions drop constraint auctions_status_check;
alter table auctions add constraint auctions_status_check check (status in ('SCHEDULED','OPEN','CLOSED','CANCELLED'));
