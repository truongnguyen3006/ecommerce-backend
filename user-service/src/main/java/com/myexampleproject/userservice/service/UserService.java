package com.myexampleproject.userservice.service;

import com.myexampleproject.userservice.dto.UserRequest;
import com.myexampleproject.userservice.dto.UserResponse;
import com.myexampleproject.userservice.model.User;
import com.myexampleproject.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

@RequiredArgsConstructor
@Service // Đánh dấu đây là một Bean Service
public class UserService {
    private final UserRepository userRepository;

    // Keycloak Admin API
    private final WebClient keycloakClient = WebClient.builder()
            .baseUrl("http://keycloak:8085/admin/realms/spring-boot-microservices-realm")
            .defaultHeader("Authorization", "Bearer <admin-access-token>") // bạn có thể inject qua config
            .build();

    public List<UserResponse> getAllUsers(){
        List<User> users = userRepository.findAll();
        return users.stream().map(this::mapToUserResponse).toList();
    }

    public UserResponse getUserById(Long id){
        User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));
        return mapToUserResponse(user);
    }

    // Tạo user trong Keycloak + DB app
    public UserResponse createUser(UserRequest userRequest){
        // 1️⃣ Gọi API Keycloak để tạo user
        String keycloakId = createUserInKeycloak(userRequest);

        // 2️⃣ Lưu user profile vào DB
        User user = User.builder()
                .keycloakId(keycloakId)
                .fullName(userRequest.getFullName())
                .email(userRequest.getEmail())
                .phoneNumber(userRequest.getPhoneNumber())
                .address(userRequest.getAddress())
                .build();

        userRepository.save(user);
        return mapToUserResponse(user);
    }

    private String createUserInKeycloak(UserRequest req) {
        // Gửi request tạo user tới Keycloak Admin REST API
        // (Giả lập logic, bạn có thể dùng WebClient hoặc Keycloak Admin Client SDK)
        // POST /admin/realms/{realm}/users
        // Sau khi tạo user, Keycloak trả về Location header chứa ID user

        // Tạm thời giả lập trả về ID ngẫu nhiên
        return java.util.UUID.randomUUID().toString();
    }

    public void deleteUserById(Long id){
        // Thêm logic kiểm tra nếu cần
        if(!userRepository.existsById(id)){
            throw new RuntimeException("User not found");
        }
        userRepository.deleteById(id);
    }

    private UserResponse mapToUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .keycloakId(user.getKeycloakId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .address(user.getAddress())
                .build();
    }

}
