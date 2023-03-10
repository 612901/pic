package cn.hellohao;

import javax.servlet.MultipartConfigElement;
import javax.servlet.http.HttpServletRequest;

import cn.hellohao.utils.Print;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.util.unit.DataSize;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.InetAddress;
import java.util.Scanner;

@SpringBootApplication
@Configuration
@EnableScheduling
@ServletComponentScan
@EnableTransactionManagement(proxyTargetClass = true)
public class TbedApplication {

public static void main(String[] args) {
    SpringApplication.run(TbedApplication.class, args);
    }
    /**
     * 文件上传配置
     * @return
     */
    @Bean
    public MultipartConfigElement multipartConfigElement() {
        MultipartConfigFactory factory = new MultipartConfigFactory();
        //  单个数据大小
        factory.setMaxFileSize(DataSize.parse("102400KB")); // KB,MB
        /// 总上传数据大小
        return factory.createMultipartConfig();
    }

}

