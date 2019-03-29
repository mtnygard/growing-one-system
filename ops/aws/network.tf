resource "aws_key_pair" "keypair" {
  key_name   = "igles-owner"
  public_key = "${var.public_key}"
}

resource "aws_vpc" "vpc" {
  cidr_block = "10.0.0.0/16"

  tags {
    Name        = "vpc-igles"
    System = "igles"
    Environment = "dev"
  }
}

resource "aws_security_group" "sg-fe" {
  name        = "secgrp-fe"
  description = "Incoming Internet traffic"
  vpc_id      = "${aws_vpc.vpc.id}"

  ingress {
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group" "sg-apps" {
  name        = "secgrp-app"
  description = "Allow traffic from LB to app servers"
  vpc_id      = "${aws_vpc.vpc.id}"

  ingress {
    from_port       = "${var.backend-port}"
    to_port         = "${var.backend-port}"
    protocol        = "tcp"
    security_groups = ["${aws_security_group.sg-fe.id}"]
  }

  ingress {
    from_port       = 8080
    to_port         = 8080
    protocol        = "tcp"
    security_groups = ["${aws_security_group.sg-fe.id}"]
  }

  ingress {
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

# resource "aws_security_group" "sg-db" {
#   name        = "db-sg-${var.student_id}"
#   description = "Allow traffic from app servers to DB"
#   vpc_id      = "${aws_vpc.vpc.id}"

#   ingress {
#     from_port       = "3306"
#     to_port         = "3306"
#     protocol        = "tcp"
#     security_groups = ["${aws_security_group.sg-apps.id}"]
#   }

#   egress {
#     from_port   = 0
#     to_port     = 0
#     protocol    = "-1"
#     cidr_blocks = ["0.0.0.0/0"]
#   }
# }

resource "aws_internet_gateway" "igw" {
  vpc_id = "${aws_vpc.vpc.id}"

  tags {
    Name = "igw"
    System = "igles"
  }
}

resource "aws_subnet" "subnet-app-1" {
  vpc_id            = "${aws_vpc.vpc.id}"
  cidr_block        = "10.0.0.0/20"
  availability_zone = "${data.aws_availability_zones.available.names[0]}"

  tags {
    Name = "subnet-app-1"
    System = "igles"
  }
}

resource "aws_subnet" "subnet-app-2" {
  vpc_id            = "${aws_vpc.vpc.id}"
  cidr_block        = "10.0.16.0/20"
  availability_zone = "${data.aws_availability_zones.available.names[1]}"

  tags {
    Name = "subnet-app-2"
    System = "igles"
  }
}

#resource "aws_subnet" "subnet-app-3" {
#  vpc_id            = "${aws_vpc.vpc.id}"
#  cidr_block        = "10.0.32.0/20"
#  availability_zone = "${data.aws_availability_zones.available.names[2]}"
#
#  tags {
#    Name = "subnet-app-3"
#  }
#}

resource "aws_route_table" "rt" {
  vpc_id = "${aws_vpc.vpc.id}"

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = "${aws_internet_gateway.igw.id}"
  }

  tags {
    Name = "route-table"
    System = "igles"
  }
}

resource "aws_main_route_table_association" "rt-assoc" {
  vpc_id         = "${aws_vpc.vpc.id}"
  route_table_id = "${aws_route_table.rt.id}"
}

# resource "aws_db_subnet_group" "subg-db" {
#   name = "subg-db"

#   subnet_ids = ["${aws_subnet.subnet-app-1.id}",
#     "${aws_subnet.subnet-app-2.id}",
#   ]

#   tags {
#     Name = "subg-db"
#   }
# }
