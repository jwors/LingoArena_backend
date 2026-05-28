-- 插入 CET-4 词库
INSERT INTO wordbooks (name, description, level, is_active)
VALUES ('四级核心词汇', '大学英语四级考试核心词汇', 'CET4', TRUE);

-- 插入 CET-6 词库
INSERT INTO wordbooks (name, description, level, is_active)
VALUES ('六级核心词汇', '大学英语六级考试核心词汇', 'CET6', TRUE);

-- 插入考研词库
INSERT INTO wordbooks (name, description, level, is_active)
VALUES ('考研核心词汇', '全国硕士研究生入学考试英语核心词汇', 'KAOYAN', TRUE);

-- 注意：实际种子数据会通过 Java 代码从 JSON 文件读取并写入 words 表
-- 这里的 INSERT 仅为示例，详细单词导入在后续迁移或 CommandLineRunner 中完成
