package io.roastedroot.treesitter.query.xml;

import io.roastedroot.treesitter.Language;
import io.roastedroot.treesitter.TreeSitter;
import io.roastedroot.treesitter.TreeSitterNode;
import io.roastedroot.treesitter.TreeSitterQueryResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QueryPomDependencyTest {

    @Test
    void queryPom() {
        String source = """
                <?xml version="1.0" encoding="UTF-8"?>
                 <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                 	xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                 	<modelVersion>4.0.0</modelVersion>
                 	<groupId>com.todo</groupId>
                 	<artifactId>app</artifactId>
                 	<version>0.0.1-SNAPSHOT</version>
                 	<name>app</name>
                 	<description>Demo project for Spring Boot</description>
                 	<properties>
                 		<java.version>21</java.version>
                 	</properties>
                 	<parent>
                 		<groupId>org.springframework.boot</groupId>
                 		<artifactId>spring-boot-starter-parent</artifactId>
                 		<version>3.5.3</version>
                 		<relativePath/> <!-- lookup parent from repository -->
                 	</parent>
                 	<dependencies>
                 		<dependency>
                 			<groupId>org.springframework.boot</groupId>
                 			<artifactId>spring-boot-starter-data-jpa</artifactId>
                 		</dependency>
                 		<dependency>
                 			<groupId>org.springframework.boot</groupId>
                 			<artifactId>spring-boot-starter-thymeleaf</artifactId>
                 		</dependency>
                 		<dependency>
                 			<groupId>org.springframework.boot</groupId>
                 			<artifactId>spring-boot-starter-web</artifactId>
                 		</dependency>
                
                 		<dependency>
                 			<groupId>org.springframework.boot</groupId>
                 			<artifactId>spring-boot-devtools</artifactId>
                 			<scope>runtime</scope>
                 			<optional>true</optional>
                 		</dependency>
                 		<dependency>
                 			<groupId>com.mysql</groupId>
                 			<artifactId>mysql-connector-j</artifactId>
                 			<scope>runtime</scope>
                 		</dependency>
                 		<dependency>
                 			<groupId>org.springframework.boot</groupId>
                 			<artifactId>spring-boot-starter-test</artifactId>
                 			<scope>test</scope>
                 		</dependency>
                
                 		<!-- <dependency>
                 			<groupId>org.springframework.boot</groupId>
                 			<artifactId>spring-boot-starter-security</artifactId>
                 		</dependency> -->
                 			<dependency>
                 				<groupId>io.jsonwebtoken</groupId>
                 				<artifactId>jjwt</artifactId>
                 				<version>0.9.1</version>
                 		</dependency>
                 	</dependencies>
                
                 	<build>
                 		<plugins>
                 			<plugin>
                 				<groupId>org.springframework.boot</groupId>
                 				<artifactId>spring-boot-maven-plugin</artifactId>
                 			</plugin>
                 		</plugins>
                 	</build>
                 </project>
                """;

        String query = """
                (element
                  (STag (Name) @tag.dep (#eq? @tag.dep "dependency"))
                  (content
                    (element
                      (STag (Name) @tag.g (#eq? @tag.g "groupId"))
                      (content (CharData) @group.id))
                    (element
                      (STag (Name) @tag.a (#eq? @tag.a "artifactId"))
                      (content (CharData) @artifact.id))
                  )) @dependency.block
                """;

        try (var ts = TreeSitter.create();
             var parser = ts.newParser()) {

            parser.setLanguage(Language.XML);

            try (var tree = parser.parseString(source);
                 var treeQuery = ts.newQuery(Language.XML,
                         query)) {

                TreeSitterNode rootNode = tree.rootNode();

                assertEquals(1, treeQuery.patternCount());
                assertEquals(6, treeQuery.captureCount());
                assertEquals("tag.dep", treeQuery.captureName(0));

                List<TreeSitterQueryResult> captures = treeQuery.exec(rootNode, source);
                assertFalse(captures.isEmpty());

                TreeSitterQueryResult capture = captures.get(0);
                assertEquals("dependency.block", capture.name());
                assertEquals("element", capture.node().type());

                String pom = source.substring(
                        capture.node().startByte(),
                        capture.node().endByte());
                assertTrue(pom.contains("<groupId>org.springframework.boot</groupId>"));
            }
        }
    }
}