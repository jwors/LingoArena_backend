package com.lingoarena.mapper;

import com.lingoarena.dto.response.AuthResponse;
import com.lingoarena.entity.User;
import org.mapstruct.Mapper;

/**
 * User 实体和 DTO 之间的映射。
 * MapStruct 会在编译期自动生成实现代码，零运行时反射开销。
 *
 * componentModel = "spring" 让 MapStruct 生成 Spring Bean，
 * 其他地方可以 @Autowired 或构造器注入。
 */
@Mapper(componentModel = "spring")
public interface UserMapper {

    /** 将 User 实体转换为 AuthResponse.UserInfo（只暴露必要字段） */
    AuthResponse.UserInfo toUserInfo(User user);
}
