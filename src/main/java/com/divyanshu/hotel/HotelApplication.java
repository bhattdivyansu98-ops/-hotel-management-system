package com.divyanshu.hotel;

import com.divyanshu.hotel.app.DemoDataSeeder;
import com.divyanshu.hotel.app.HotelContext;
import com.divyanshu.hotel.config.AppConfig;
import com.divyanshu.hotel.db.Database;
import com.divyanshu.hotel.db.SchemaInitializer;
import com.divyanshu.hotel.web.ApiServer;

import javax.sql.DataSource;

public final class HotelApplication {

    private HotelApplication() {
    }

    public static void main(String[] args) {
        AppConfig config = AppConfig.fromEnvironment();
        DataSource dataSource = Database.pooledDataSource(config);
        SchemaInitializer.initialize(dataSource);

        HotelContext hotel = new HotelContext(dataSource, config);
        if (config.seedDemoData()) {
            DemoDataSeeder.seedRooms(hotel.rooms());
        }

        new ApiServer(hotel).create().start(config.port());
    }
}
