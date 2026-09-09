package trading.demo.service;

import trading.demo.repository.AddressRepository;

import trading.demo.model.entity.AddressEntity;

public class AddresServiceImpl {

    private AddressRepository addressRepository;

    public AddressEntity createAddress(AddressEntity address) {
        return addressRepository.save(address);
    }
}
