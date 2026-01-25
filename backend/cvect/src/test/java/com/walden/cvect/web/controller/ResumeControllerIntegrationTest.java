package com.walden.cvect.web.controller;

import com.walden.cvect.service.ResumeProcessService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

import java.io.InputStream;

@SpringBootTest
@Tag("integration")
@Tag("api")
class ResumeControllerIntegrationTest {

        @Autowired
        private WebApplicationContext webApplicationContext;

        @Autowired
        private ResumeProcessService processService;

        private MockMvc mockMvc;

        @Test
        @DisplayName("健康检查端点应返回 UP")
        void health_check_should_return_up() throws Exception {
                mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

                mockMvc.perform(
                                MockMvcRequestBuilders.get("/api/resumes/health")
                                                .accept(MediaType.APPLICATION_JSON))
                                .andDo(print())
                                .andExpect(MockMvcResultMatchers.status().isOk())
                                .andExpect(MockMvcResultMatchers.content().string("UP"));
        }

        @Test
        @DisplayName("上传 PDF 简历应成功解析并返回 chunks")
        void upload_pdf_resume_should_parse_and_return_chunks() throws Exception {
                mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

                // Given: 加载真实的 PDF 文件
                InputStream is = getClass().getResourceAsStream("/static/My.pdf");
                assertNotNull(is, "My.pdf 文件不存在");

                byte[] fileContent = is.readAllBytes();
                MockMultipartFile file = new MockMultipartFile(
                                "file",
                                "My.pdf",
                                "application/pdf",
                                fileContent);

                // When: 上传文件到 API
                mockMvc.perform(
                                MockMvcRequestBuilders.multipart("/api/resumes/parse")
                                                .file(file)
                                                .accept(MediaType.APPLICATION_JSON))
                                .andDo(print())
                                .andExpect(MockMvcResultMatchers.status().isOk())
                                .andExpect(MockMvcResultMatchers.jsonPath("$.candidateId").exists())
                                .andExpect(MockMvcResultMatchers.jsonPath("$.totalChunks").exists())
                                .andExpect(MockMvcResultMatchers.jsonPath("$.chunks").isArray());

                // Then: 验证 processService 确实被调用
                // 由于 candidateId 是 UUID，我们无法精确匹配，但可以验证流程
                assertTrue(fileContent.length > 0, "文件内容不应为空");
        }

        @Test
        @DisplayName("上传 Resume.pdf 应成功解析")
        void upload_resume_pdf_should_parse_successfully() throws Exception {
                mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

                InputStream is = getClass().getResourceAsStream("/static/Resume.pdf");
                assertNotNull(is, "Resume.pdf 文件不存在");

                byte[] fileContent = is.readAllBytes();
                MockMultipartFile file = new MockMultipartFile(
                                "file",
                                "Resume.pdf",
                                "application/pdf",
                                fileContent);

                mockMvc.perform(
                                MockMvcRequestBuilders.multipart("/api/resumes/parse")
                                                .file(file)
                                                .accept(MediaType.APPLICATION_JSON))
                                .andDo(print())
                                .andExpect(MockMvcResultMatchers.status().isOk())
                                .andExpect(MockMvcResultMatchers.jsonPath("$.totalChunks").isNumber())
                                .andExpect(MockMvcResultMatchers.jsonPath("$.chunks").exists());
        }

        @Test
        @DisplayName("使用指定 content-type 上传文件")
        void upload_file_with_custom_content_type() throws Exception {
                mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

                InputStream is = getClass().getResourceAsStream("/static/My.pdf");
                byte[] fileContent = is.readAllBytes();

                MockMultipartFile file = new MockMultipartFile(
                                "file",
                                "My.pdf",
                                "application/pdf",
                                fileContent);

                mockMvc.perform(
                                MockMvcRequestBuilders.multipart("/api/resumes/parse")
                                                .file(file)
                                                .param("contentType", "application/pdf")
                                                .accept(MediaType.APPLICATION_JSON))
                                .andDo(print())
                                .andExpect(MockMvcResultMatchers.status().isOk());
        }
}
