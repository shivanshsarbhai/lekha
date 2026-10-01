CREATE TABLE categories (
    id         UUID         PRIMARY KEY,
    parent_id  UUID         REFERENCES categories (id),
    name       VARCHAR(40)  NOT NULL,
    kind       VARCHAR(20)  NOT NULL CHECK (kind IN ('EXPENSE', 'INCOME', 'INVESTMENT')),
    created_at TIMESTAMPTZ  NOT NULL
);

CREATE UNIQUE INDEX categories_name_unique ON categories (kind, parent_id, lower(name)) NULLS NOT DISTINCT;

CREATE INDEX categories_parent_idx ON categories (parent_id);

-- A starting set. These are ordinary rows: they can be renamed or deleted like any other category.

INSERT INTO categories (id, parent_id, name, kind, created_at)
SELECT gen_random_uuid(), NULL, top.name, top.kind, now()
FROM (VALUES
    ('Bills',                 'EXPENSE'),
    ('Subscriptions',         'EXPENSE'),
    ('Food',                  'EXPENSE'),
    ('Travel',                'EXPENSE'),
    ('Shopping',              'EXPENSE'),
    ('Health',                'EXPENSE'),
    ('Activities',            'EXPENSE'),
    ('Personal care',         'EXPENSE'),
    ('Family',                'EXPENSE'),
    ('Gifts & donations',     'EXPENSE'),
    ('Insurance',             'EXPENSE'),
    ('Fees & charges',        'EXPENSE'),
    ('Miscellaneous',         'EXPENSE'),
    ('Salary',                'INCOME'),
    ('Interest',              'INCOME'),
    ('Dividends',             'INCOME'),
    ('Freelance',             'INCOME'),
    ('Gifts received',        'INCOME'),
    ('Mutual funds',          'INVESTMENT'),
    ('Stocks',                'INVESTMENT'),
    ('Fixed deposits',        'INVESTMENT'),
    ('PPF / EPF',             'INVESTMENT'),
    ('NPS',                   'INVESTMENT'),
    ('Gold',                  'INVESTMENT')
) AS top (name, kind);

INSERT INTO categories (id, parent_id, name, kind, created_at)
SELECT gen_random_uuid(), parent.id, child.name, parent.kind, now()
FROM (VALUES
    ('Bills',                 'Rent'),
    ('Bills',                 'Maintenance'),
    ('Bills',                 'Cook & house help'),
    ('Bills',                 'Electricity'),
    ('Bills',                 'Water & gas'),
    ('Bills',                 'Mobile & WiFi'),
    ('Subscriptions',         'Cloud storage'),
    ('Subscriptions',         'Music'),
    ('Subscriptions',         'Video streaming'),
    ('Subscriptions',         'Apps & software'),
    ('Food',                  'Dining out'),
    ('Food',                  'Ordering in'),
    ('Food',                  'Groceries'),
    ('Travel',                'Petrol'),
    ('Travel',                'Parking & tolls'),
    ('Travel',                'Metro & bus'),
    ('Travel',                'Autos & cabs'),
    ('Travel',                'Flights'),
    ('Travel',                'Trains'),
    ('Travel',                'Hotels & stays'),
    ('Shopping',              'Clothing & accessories'),
    ('Shopping',              'Electronics'),
    ('Shopping',              'Home & kitchen'),
    ('Health',                'Gym membership'),
    ('Health',                'Sports'),
    ('Health',                'Medicines'),
    ('Health',                'Doctor & tests'),
    ('Activities',            'Movies'),
    ('Activities',            'Workshops'),
    ('Activities',            'Events & concerts'),
    ('Insurance',             'Health insurance'),
    ('Insurance',             'Life insurance'),
    ('Insurance',             'Vehicle insurance')
) AS child (parent_name, name)
JOIN categories parent ON parent.name = child.parent_name AND parent.parent_id IS NULL;
