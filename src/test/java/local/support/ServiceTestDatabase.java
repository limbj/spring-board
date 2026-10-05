package local.support;

import java.util.UUID;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import io.github.limbj.board.mapper.BoardAttachMapper;
import io.github.limbj.board.mapper.BoardMapper;
import io.github.limbj.board.mapper.ReplyMapper;
import io.github.limbj.board.service.BoardService;
import io.github.limbj.board.service.BoardServiceImpl;
import io.github.limbj.board.service.ReplyService;
import io.github.limbj.board.service.ReplyServiceImpl;

/** Test-only configuration kept outside the application's component scan. */
@Configuration(proxyBeanMethods = false)
@EnableTransactionManagement
public class ServiceTestDatabase {
    @Bean public DataSource dataSource() {
        DriverManagerDataSource source = new DriverManagerDataSource(
                "jdbc:h2:mem:board_services_" + UUID.randomUUID() + ";MODE=Oracle;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("db/schema-h2.sql")).execute(source);
        return source;
    }

    @Bean public SqlSessionFactory sqlSessionFactory(DataSource source) throws Exception {
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(source);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:io/github/limbj/board/mapper/*.xml"));
        return factory.getObject();
    }

    @Bean public DataSourceTransactionManager transactionManager(DataSource source) {
        return new DataSourceTransactionManager(source);
    }

    @Bean public BoardMapper boardMapper(SqlSessionFactory factory) throws Exception { return mapper(BoardMapper.class, factory); }
    @Bean public ReplyMapper replyMapper(SqlSessionFactory factory) throws Exception { return mapper(ReplyMapper.class, factory); }
    @Bean public BoardAttachMapper attachMapper(SqlSessionFactory factory) throws Exception { return mapper(BoardAttachMapper.class, factory); }
    @Bean public BoardService boardService(BoardMapper board, BoardAttachMapper attach, ReplyMapper reply) {
        return new BoardServiceImpl(board, attach, reply);
    }
    @Bean public ReplyService replyService(ReplyMapper reply, BoardMapper board) { return new ReplyServiceImpl(reply, board); }

    private <T> T mapper(Class<T> type, SqlSessionFactory factory) throws Exception {
        MapperFactoryBean<T> bean = new MapperFactoryBean<>(type);
        bean.setSqlSessionFactory(factory);
        bean.afterPropertiesSet();
        return bean.getObject();
    }
}
