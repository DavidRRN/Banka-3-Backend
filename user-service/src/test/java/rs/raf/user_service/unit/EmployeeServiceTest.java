package rs.raf.user_service.unit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import rs.raf.user_service.domain.dto.CreateEmployeeDto;
import rs.raf.user_service.domain.dto.EmployeeDto;
import rs.raf.user_service.domain.dto.UpdateEmployeeDto;
import rs.raf.user_service.domain.entity.Employee;
import rs.raf.user_service.domain.entity.Role;
import rs.raf.user_service.repository.AuthTokenRepository;
import rs.raf.user_service.repository.EmployeeRepository;
import rs.raf.user_service.repository.RoleRepository;
import rs.raf.user_service.repository.UserRepository;
import rs.raf.user_service.service.EmployeeService;

import javax.persistence.EntityNotFoundException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private AuthTokenRepository authTokenRepository;
    @Mock
    private RabbitTemplate rabbitTemplate;
    @Mock
    private RoleRepository roleRepository;

    @InjectMocks
    private EmployeeService employeeService;


    @Test
    void testFindById() {
        Employee employee = new Employee();
        employee.setUsername("marko123");
        employee.setPosition("Manager");
        employee.setDepartment("Finance");
        employee.setActive(true);
        employee.setRole(new Role(1L, "EMPLOYEE", new HashSet<>()));

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));

        EmployeeDto result = employeeService.findById(1L);

        assertNotNull(result);
        assertEquals("marko123", result.getUsername());
        assertEquals("Manager", result.getPosition());
    }

    @Test
    void testFindAllWithPaginationAndFilters() {
        Employee employee = new Employee();
        employee.setUsername("jovan456");
        employee.setFirstName("Jovan");
        employee.setLastName("Jovanovic");
        employee.setEmail("jovan456@example.com");
        employee.setPosition("Developer");
        employee.setDepartment("IT");
        employee.setActive(true);
        employee.setRole(new Role(1L, "EMPLOYEE", new HashSet<>()));

        Page<Employee> page = new PageImpl<>(Collections.singletonList(employee));

        when(employeeRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        Page<EmployeeDto> result = employeeService.findAll("Jovan", "Jovanovic", "jovan456@example.com", "Developer", PageRequest.of(0, 10));

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("Developer", result.getContent().get(0).getPosition());
        assertEquals("IT", result.getContent().get(0).getDepartment());
        assertTrue(result.getContent().get(0).isActive());
    }

    @Test
    void testFindAllWithoutFilters() {
        Role role = new Role(1L, "EMPLOYEE", new HashSet<>());

        Employee emp1 = new Employee();
        emp1.setUsername("ana789");
        //emp1.setFirstName("Ana");
        //emp1.setLastName("Anicic");
        //emp1.setEmail("ana789@example.com");
        emp1.setPosition("HR");
        emp1.setDepartment("Human Resources");
        emp1.setActive(true);
        emp1.setRole(role);

        Employee emp2 = new Employee();
        emp2.setUsername("ivan321");
        emp2.setPosition("Designer");
        emp2.setDepartment("Creative");
        emp2.setActive(false);
        emp2.setRole(role);

        Page<Employee> page = new PageImpl<>(List.of(emp1, emp2));

        when(employeeRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        Page<EmployeeDto> result = employeeService.findAll(null, null, null, null, PageRequest.of(0, 10));

        assertNotNull(result);
        assertEquals(2, result.getTotalElements());
    }

    @Test
    void testFindByIdNotFound() {
        when(employeeRepository.findById(99L)).thenReturn(Optional.empty());

        Exception exception = assertThrows(RuntimeException.class, () -> employeeService.findById(99L));

        assertEquals(EntityNotFoundException.class, exception.getClass());
    }

    @Test
    void testDeleteEmployee() {
        Employee employee = new Employee();
        employee.setId(1L);
        employee.setUsername("marko123");
        employee.setPosition("Manager");
        employee.setDepartment("Finance");
        employee.setActive(true);

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));

        employeeService.deleteEmployee(1L);

        verify(employeeRepository, times(1)).delete(employee);
    }

    @Test
    void testDeleteEmployeeNotFound() {
        when(employeeRepository.findById(99L)).thenReturn(Optional.empty());

        Exception exception = assertThrows(RuntimeException.class, () -> employeeService.deleteEmployee(99L));

        assertEquals("Employee not found", exception.getMessage());
        verify(employeeRepository, never()).delete(any()); // Proveravamo da delete nije pozvan
    }

    @Test
    void testDeactivateEmployee() {
        Employee employee = new Employee();
        employee.setId(1L);
        employee.setUsername("marko123");
        employee.setPosition("Manager");
        employee.setDepartment("Finance");
        employee.setActive(true);

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));

        employeeService.deactivateEmployee(1L);

        assertFalse(employee.isActive());
        verify(employeeRepository, times(1)).save(employee); // Proveravamo da je save pozvan
    }

    @Test
    void testDeactivateEmployeeNotFound() {
        when(employeeRepository.findById(99L)).thenReturn(Optional.empty());

        Exception exception = assertThrows(RuntimeException.class, () -> employeeService.deactivateEmployee(99L));

        assertEquals("Employee not found", exception.getMessage());
        verify(employeeRepository, never()).save(any()); // Proveravamo da save nije pozvan
    }

    @Test
    void testActivateEmployee() {
        Employee employee = new Employee();
        employee.setId(1L);
        employee.setUsername("marko123");
        employee.setPosition("Manager");
        employee.setDepartment("Finance");
        employee.setActive(false);

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));

        employeeService.activateEmployee(1L);

        assertTrue(employee.isActive());
        verify(employeeRepository, times(1)).save(employee);
    }

    @Test
    void testActivateEmployeeNotFound() {
        when(employeeRepository.findById(99L)).thenReturn(Optional.empty());

        Exception exception = assertThrows(RuntimeException.class, () -> employeeService.activateEmployee(99L));

        assertEquals("Employee not found", exception.getMessage());
        verify(employeeRepository, never()).save(any());
    }

    @Test
    void testCreateEmployee() {
        String firstName = "Petar";
        String lastName = "Petrovic";
        String gender = "M";
        String email = "petarw@raf.rs";
        String phone = "+38161123456";
        String address = "Trg Republike 5";
        String username = "petareperic90";
        String position = "Menadzer";
        String department = "Finansije";
        String jmbg = "1234567890123";
        String role = "EMPLOYEE";
        Boolean active = true;

        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        calendar.set(1990, 1, 20, 0, 0, 0);
        Date birthDate = calendar.getTime();

        Role expectedRole = new Role();
        expectedRole.setId(2L);
        expectedRole.setName("EMPLOYEE");
        when(roleRepository.findByName(any())).thenReturn(Optional.of(expectedRole));

        employeeService.createEmployee(new CreateEmployeeDto(firstName, lastName, birthDate, gender, email, active, phone, address,
                username, position, department, jmbg, role)
        );

        verify(employeeRepository, times(1)).save(argThat(employee ->
                employee.getFirstName().equals(firstName) &&
                        employee.getLastName().equals(lastName) &&
                        employee.getBirthDate().equals(birthDate) &&
                        employee.getGender().equals(gender) &&
                        employee.getEmail().equals(email) &&
                        employee.getPhone().equals(phone) &&
                        employee.getAddress().equals(address) &&
                        employee.getUsername().equals(username) &&
                        employee.getPosition().equals(position) &&
                        employee.getDepartment().equals(department)
        ));
    }

    @Test
    void testUpdateEmployee() {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        calendar.set(1990, 1, 20, 0, 0, 0);

        Employee employee = new Employee("Petar", "Petrovic", calendar.getTime(), "M",
                "petar@raf.rs", "+38161123456", "Trg Republike 5", "petareperic90",
                "Menadzer", "Finansije", true, "1234567890123",
                new Role(1L, "EMPLOYEE", new HashSet<>())
        );

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(roleRepository.findByName(any())).thenReturn(Optional.of(new Role(1L, "EMPLOYEE", new HashSet<>())));

        String lastName = "Peric";
        String gender = "F";
        String phone = "+38161123457";
        String address = "Trg Republike 6";
        String position = "Programer";
        String department = "Programiranje";
        String role = "EMPLOYEE";

        employeeService.updateEmployee(1L, new UpdateEmployeeDto(lastName, gender, phone, address, position, department, role));

        assertAll("Employee fields should be updated correctly",
                () -> assertEquals(lastName, employee.getLastName()),
                () -> assertEquals(gender, employee.getGender()),
                () -> assertEquals(phone, employee.getPhone()),
                () -> assertEquals(address, employee.getAddress()),
                () -> assertEquals(position, employee.getPosition()),
                () -> assertEquals(department, employee.getDepartment())
        );
        verify(employeeRepository, times(1)).save(employee);
    }

    @Test
    void testUpdateEmployeeNotFound() {
        when(employeeRepository.findById(99L)).thenReturn(Optional.empty());

        Exception exception = assertThrows(EntityNotFoundException.class, () -> employeeService.updateEmployee(
                        99L, new UpdateEmployeeDto("Peric", "F", "+38161123457",
                                "Trg Republike 6", "Programer", "Programiranje", "EMPLOYEE")
                )
        );

        assertEquals("Employee not found", exception.getMessage());
        verify(employeeRepository, never()).save(any());
    }


}
