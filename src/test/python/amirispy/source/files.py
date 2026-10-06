# SPDX-FileCopyrightText: 2022 German Aerospace Center <amiris@dlr.de>
#
# SPDX-License-Identifier: Apache-2.0

import os
from typing import List

CSV_FILE_ENDING = ".csv".upper()
PATH_SEPARATOR = "/"
ENDING_SEPARATOR = "."


def get_all_csv_files_in_folder_except(
    folder: str, exceptions: List[str] = None
) -> List[str]:
    """
    Find all csv files in a folder that can optionally ignore a files with a given file name
    Args:
        folder: to search for csv files - file ending is **not** case sensitive
        exceptions: optional, files names (without file ending) listed here will be ignored - **not** case sensitive
    Returns: Full file names including folder path for files ending with ".csv" not listed in exceptions
    """
    if exceptions is None:
        exceptions = list()
    exceptions = [item.upper() for item in exceptions]
    return [
        folder + PATH_SEPARATOR + file
        for file in os.listdir(folder)
        if file.upper().endswith(CSV_FILE_ENDING)
        and file.upper()[:-4] not in exceptions
    ]


def get_file_name_stem(file_path: str) -> str:
    """
    Get file name without path and ending
    Args:
        file_path: file name that might have a file ending and or path
    Returns: stem of file name without ending and path
    """
    result = file_path.split(PATH_SEPARATOR)[-1]
    if ENDING_SEPARATOR in result:
        ending = result.split(ENDING_SEPARATOR)[-1]
        ending_length = len(ending) + 1
        result = result[:-ending_length]
    return result
