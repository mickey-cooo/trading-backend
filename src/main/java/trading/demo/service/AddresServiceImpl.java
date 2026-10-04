package trading.demo.service;

import org.springframework.stereotype.Service;
import trading.demo.repository.AddressRepository;
import trading.demo.model.entity.AddressEntity;

@Service
public class AddresServiceImpl implements AddressService {

	private final AddressRepository addressRepository;

	public AddresServiceImpl(AddressRepository addressRepository) {
		this.addressRepository = addressRepository;
	}

	@Override
	public AddressEntity createAddress(AddressEntity address) {
		return addressRepository.save(address);
	}
}
