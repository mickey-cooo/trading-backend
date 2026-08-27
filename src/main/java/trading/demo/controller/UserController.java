package trading.demo.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import trading.demo.model.entity.UserEntity;
import trading.demo.service.UserService;

@RestController()
@RequestMapping("api/v1/user")
public class UserController {
	@Autowired
	private UserService userService;

	@GetMapping("/id")
	public ResponseEntity<UserEntity> getUserById(@RequestParam String id) {
		return ResponseEntity.ok(userService.getUserById(id));
	}

	@GetMapping("/update/{id}")
	public ResponseEntity<UserEntity> updateUserProfile(@PathVariable String id) {
		return ResponseEntity.ok(userService.updateUserProfile(id));
	}
}