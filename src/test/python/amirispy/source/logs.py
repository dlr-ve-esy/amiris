# SPDX-FileCopyrightText: 2022 German Aerospace Center <amiris@dlr.de>
#
# SPDX-License-Identifier: Apache-2.0

import logging as log
from enum import Enum
from typing import NoReturn


class LogLevels(Enum):
    """Levels for Logging"""

    CRITICAL = log.CRITICAL
    ERROR = log.ERROR
    WARN = log.WARNING
    INFO = log.INFO
    DEBUG = log.DEBUG


def log_and_raise_critical(message: str) -> NoReturn:
    """
    Raises and logs a critical error with given message
    Args:
        message: to be used in Exception and logging
    """
    log.critical(message)
    raise Exception(message)


def log_error_and_raise(exception: Exception) -> NoReturn:
    """
    Raises the given `exception` and logs an error with message derived from the exceptions message
    Args:
        exception: to be raised and whose message will be logged as error
    """
    log.error(str(exception))
    raise exception


def set_up_logger(log_level: LogLevels, log_file_name: str = None) -> None:
    """
    Sets up root logger which always writes to console and optionally to file
    Args:
        log_level: the logging level to apply
        log_file_name: optional - if provided, logs will also be written to file
    """
    if log_level is LogLevels.DEBUG:
        formatter_string = "%(asctime)s.%(msecs)03d — %(levelname)s — %(module)s:%(funcName)s:%(lineno)d — %(message)s"  # noqa
    else:
        formatter_string = "%(asctime)s — %(levelname)s — %(message)s"  # noqa

    log_formatter = log.Formatter(formatter_string, "%H:%M:%S")
    root_logger = log.getLogger()
    root_logger.setLevel(log_level.value)

    if log_file_name:
        file_handler = log.FileHandler(log_file_name, mode="w")
        file_handler.setFormatter(log_formatter)
        root_logger.addHandler(file_handler)

    console_handler = log.StreamHandler()
    console_handler.setFormatter(log_formatter)
    root_logger.addHandler(console_handler)
