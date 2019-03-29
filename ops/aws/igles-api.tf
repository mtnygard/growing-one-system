variable "backend-port" {
  description = "API service listens on this port"
  default     = 3000
}

resource "aws_lb" "lb-igles-api" {
  name               = "lb-igles-api"
  security_groups    = ["${aws_security_group.sg-fe.id}"]
  internal           = false
  load_balancer_type = "application"

  subnets = ["${aws_subnet.subnet-app-1.id}",
    "${aws_subnet.subnet-app-2.id}",
  ]

  tags {
    Name = "lb-igles-api"
    System = "igles"
  }
}

resource "aws_lb_target_group" "tg-igles-api" {
  name        = "tg-igles-api"
  port        = "${var.backend-port}"
  protocol    = "HTTP"
  vpc_id      = "${aws_vpc.vpc.id}"
  target_type = "instance"
}

resource "aws_lb_listener" "listen-igles-api" {
  load_balancer_arn = "${aws_lb.lb-igles-api.arn}"
  port              = "80"
  protocol          = "HTTP"

  default_action {
    target_group_arn = "${aws_lb_target_group.tg-igles-api.arn}"
    type             = "forward"
  }
}

resource "aws_launch_configuration" "lc-igles-api" {
  name_prefix                 = "lb-igles-api"
  image_id                    = "${data.aws_ami.ubuntu.id}"
  instance_type               = "t2.micro"
  key_name                    = "${aws_key_pair.keypair.key_name}"
  security_groups             = ["${aws_security_group.sg-apps.id}"]
  associate_public_ip_address = true

  #user_data = "${data.template_file.cloud-init-lobsters.rendered}"

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_autoscaling_group" "asg-igles-api" {
  name                 = "asg-igles-api-"
  launch_configuration = "${aws_launch_configuration.lc-igles-api.id}"

  vpc_zone_identifier = ["${aws_subnet.subnet-app-1.id}",
    "${aws_subnet.subnet-app-2.id}",
  ]

  min_size     = 2
  max_size     = 2
  force_delete = true

  target_group_arns = ["${aws_lb_target_group.tg-igles-api.arn}"]

  lifecycle {
    create_before_destroy = true
  }

  tag {
    key                 = "Name"
    value               = "asg-igles-api"
    propagate_at_launch = false
  }
}
