package trading.demo.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import trading.demo.model.entity.AddressEntity;

@Repository
public interface AddressRepository extends JpaRepository<AddressEntity, UUID> {

}
