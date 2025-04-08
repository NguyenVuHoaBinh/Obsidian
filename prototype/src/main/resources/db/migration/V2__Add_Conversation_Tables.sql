-- V2__Create_Tool_Tables.sql
CREATE TABLE IF NOT EXISTS tool (
                                    id INT PRIMARY KEY AUTO_INCREMENT,
                                    name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT NOT NULL,
    endpoint VARCHAR(255) NOT NULL,
    authentication_type VARCHAR(50),
    http_method VARCHAR(10) NOT NULL,
    timeout_ms INT NOT NULL DEFAULT 5000,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tool_name (name)
    );

CREATE TABLE IF NOT EXISTS parameter (
                                         id INT PRIMARY KEY AUTO_INCREMENT,
                                         tool_id INT NOT NULL,
                                         name VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL,
    required BOOLEAN NOT NULL DEFAULT false,
    description TEXT,
    default_value VARCHAR(255),
    FOREIGN KEY (tool_id) REFERENCES tool(id) ON DELETE CASCADE,
    UNIQUE INDEX idx_param_unique (tool_id, name)
    );

CREATE TABLE IF NOT EXISTS dependency (
                                          id INT PRIMARY KEY AUTO_INCREMENT,
                                          tool_id INT NOT NULL,
                                          depends_on_id INT NOT NULL,
                                          created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                          FOREIGN KEY (tool_id) REFERENCES tool(id) ON DELETE CASCADE,
    FOREIGN KEY (depends_on_id) REFERENCES tool(id) ON DELETE CASCADE,
    UNIQUE INDEX idx_dependency_unique (tool_id, depends_on_id)
    );

-- Insert initial tools
INSERT INTO tool (name, description, endpoint, http_method, timeout_ms) VALUES
                                                                            ('search_product', 'Tìm kiếm sản phẩm trong hệ thống theo từ khóa.', '/api/products/search', 'GET', 5000),
                                                                            ('add_product_to_order', 'Thêm một sản phẩm vào đơn hàng hiện tại với số lượng chỉ định.', '/api/orders/items', 'POST', 5000),
                                                                            ('create_quick_order', 'Tạo nhanh một đơn hàng dựa trên số tiền và mô tả sản phẩm (tuỳ chọn).', '/api/orders/quick', 'POST', 5000),
                                                                            ('select_payment_method', 'Chọn phương thức thanh toán cho đơn hàng hiện tại.', '/api/orders/payment-method', 'PUT', 5000),
                                                                            ('create_invoice', 'Tạo hóa đơn cho đơn hàng hiện tại.', '/api/invoices', 'POST', 5000),
                                                                            ('send_invoice', 'Gửi hóa đơn cho khách hàng qua kênh được chỉ định.', '/api/invoices/send', 'POST', 5000),
                                                                            ('print_invoice', 'In hóa đơn ra giấy.', '/api/invoices/print', 'POST', 5000),
                                                                            ('navigate_screen', 'Chuyển sang màn hình chức năng tương ứng trong ứng dụng.', '/api/navigation', 'POST', 3000);

-- Insert parameters for tools
-- search_product parameters
INSERT INTO parameter (tool_id, name, type, required, description) VALUES
    (1, 'keyword', 'STRING', true, 'Từ khóa tìm kiếm, thường là tên sản phẩm hoặc mã sản phẩm.');

-- add_product_to_order parameters
INSERT INTO parameter (tool_id, name, type, required, description, default_value) VALUES
                                                                                      (2, 'product_id', 'STRING', true, 'Mã định danh của sản phẩm cần thêm', null),
                                                                                      (2, 'quantity', 'NUMBER', false, 'Số lượng sản phẩm', '1');

-- create_quick_order parameters
INSERT INTO parameter (tool_id, name, type, required, description) VALUES
                                                                       (3, 'total_amount', 'NUMBER', true, 'Tổng số tiền của đơn hàng'),
                                                                       (3, 'items_description', 'STRING', false, 'Mô tả ngắn về các sản phẩm trong đơn hàng');

-- select_payment_method parameters
INSERT INTO parameter (tool_id, name, type, required, description) VALUES
    (4, 'payment_method', 'STRING', true, 'Phương thức thanh toán (cash, card, momo, vnpay, zalopay)');

-- send_invoice parameters
INSERT INTO parameter (tool_id, name, type, required, description) VALUES
                                                                       (6, 'invoice_type', 'STRING', true, 'Hình thức gửi hóa đơn (sms, zalo, email)'),
                                                                       (6, 'recipient', 'STRING', false, 'Thông tin người nhận (số điện thoại hoặc email)');

-- print_invoice parameters
INSERT INTO parameter (tool_id, name, type, required, description, default_value) VALUES
    (7, 'copies', 'NUMBER', false, 'Số bản in', '1');

-- navigate_screen parameters
INSERT INTO parameter (tool_id, name, type, required, description) VALUES
    (8, 'screen_name', 'STRING', true, 'Tên màn hình cần chuyển đến');

-- Insert dependencies
-- add_product_to_order depends on search_product
INSERT INTO dependency (tool_id, depends_on_id) VALUES (2, 1);

-- select_payment_method depends on either add_product_to_order or create_quick_order
INSERT INTO dependency (tool_id, depends_on_id) VALUES (4, 2), (4, 3);

-- create_invoice depends on select_payment_method
INSERT INTO dependency (tool_id, depends_on_id) VALUES (5, 4);

-- send_invoice depends on create_invoice
INSERT INTO dependency (tool_id, depends_on_id) VALUES (6, 5);

-- print_invoice depends on create_invoice
INSERT INTO dependency (tool_id, depends_on_id) VALUES (7, 5);