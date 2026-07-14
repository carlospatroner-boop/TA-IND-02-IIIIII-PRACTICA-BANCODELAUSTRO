package ec.edu.uteq.bancoaustro.config;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Declara un DataSource (y su PlatformTransactionManager) por sede.
 *
 * Ejercicio propuesto 1: se agrega el DataSource de Guayaquil (dsGuayaquil),
 * con prefijo de cuenta "09".
 *
 * Los PlatformTransactionManager por nodo son los que permiten que el
 * Ejercicio propuesto 2 (transferencias) haga debito+credito+registro de
 * transaccion de forma atomica dentro de un mismo nodo.
 */
@Configuration
public class DataSourceConfig {

    @Value("${datasources.cuenca.url}")
    private String cuencaUrl;
    @Value("${datasources.cuenca.username}")
    private String cuencaUser;
    @Value("${datasources.cuenca.password}")
    private String cuencaPass;

    @Value("${datasources.quito.url}")
    private String quitoUrl;
    @Value("${datasources.quito.username}")
    private String quitoUser;
    @Value("${datasources.quito.password}")
    private String quitoPass;

    @Value("${datasources.guayaquil.url}")
    private String guayaquilUrl;
    @Value("${datasources.guayaquil.username}")
    private String guayaquilUser;
    @Value("${datasources.guayaquil.password}")
    private String guayaquilPass;

    @Bean(name = "dsCuenca")
    public DataSource dsCuenca() {
        return DataSourceBuilder.create()
                .url(cuencaUrl)
                .username(cuencaUser)
                .password(cuencaPass)
                .driverClassName("org.postgresql.Driver")
                .build();
    }

    @Bean(name = "dsQuito")
    public DataSource dsQuito() {
        return DataSourceBuilder.create()
                .url(quitoUrl)
                .username(quitoUser)
                .password(quitoPass)
                .driverClassName("org.postgresql.Driver")
                .build();
    }

    @Bean(name = "dsGuayaquil")
    public DataSource dsGuayaquil() {
        return DataSourceBuilder.create()
                .url(guayaquilUrl)
                .username(guayaquilUser)
                .password(guayaquilPass)
                .driverClassName("org.postgresql.Driver")
                .build();
    }

    @Bean(name = "txCuenca")
    public PlatformTransactionManager txCuenca(@Qualifier("dsCuenca") DataSource ds) {
        return new DataSourceTransactionManager(ds);
    }

    @Bean(name = "txQuito")
    public PlatformTransactionManager txQuito(@Qualifier("dsQuito") DataSource ds) {
        return new DataSourceTransactionManager(ds);
    }

    @Bean(name = "txGuayaquil")
    public PlatformTransactionManager txGuayaquil(@Qualifier("dsGuayaquil") DataSource ds) {
        return new DataSourceTransactionManager(ds);
    }
}
