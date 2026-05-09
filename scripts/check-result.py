#!/usr/bin/python
import os
import sys

import diff


id_dict = {
    1: ["token.txt", "old_symbol_table.txt"],
    2: ["parser_list.txt"],
    3: ["ir_emulate_result.txt", "new_symbol_table.txt"],
    4: ["assembly_language.asm"],
}

rars_path = os.path.join(os.path.dirname(__file__), "rars.jar")


if __name__ == "__main__":
    _, _lab_id, std_dir, out_dir = sys.argv
    lab_id = int(_lab_id)

    if lab_id <= 3:
        diff_range = lab_id
    elif lab_id == 4:
        diff_range = 3
    else:
        raise ValueError(f"Unsupported lab id: {lab_id}")

    for i in range(1, diff_range + 1):
        print(f"Diffing lab{i} output:")
        for filename in id_dict[i]:
            out_path = os.path.join(out_dir, filename)
            std_path = os.path.join(std_dir, filename)
            print(f"Diffing file {filename}:")
            diff.do_diff(std_path, out_path)
        print()

    if lab_id == 4:
        assembly_path = os.path.join(out_dir, "assembly_language.asm")
        os.system(
            f'java -jar "{rars_path}" mc CompactDataAtZero a0 nc dec ae255 "{assembly_path}"'
        )
