export declare type SomeJSONSchema = JSONSchemaType<Known, true>;
export declare type PartialSchema<T> = Partial<JSONSchemaType<T, true>>;
declare type JSONType<T extends string, _partial extends boolean> = _partial extends true ? T | undefined : T;
export declare type JSONSchemaType<T, _partial extends boolean = false> = (T extends number ? {
    type: JSONType<"number" | "integer", _partial>;
    minimum?: number;
    maximum?: number;
    exclusiveMinimum?: number;
    exclusiveMaximum?: number;
    multipleOf?: number;
    format?: string;
} : T extends string ? {
    type: JSONType<"string", _partial>;
    minLength?: number;
    maxLength?: number;
    pattern?: string;
    format?: string;
} : T extends boolean ? {
    type: "boolean";
} : T extends [any, ...any[]] ? {
    type: JSONType<"array", _partial>;
    items: {
        [K in keyof T]-?: JSONSchemaType<T[K]> & Nullable<T[K]>;
    } & {
        length: T["length"];
    };
    minItems: T["length"];
} & ({
    maxItems: T["length"];
} | {
    additionalItems: false;
}) : T extends any[] ? {
    type: JSONType<"array", _partial>;
    items: JSONSchemaType<T[0]>;
    contains?: PartialSchema<T[0]>;
    minItems?: number;
    maxItems?: number;
    minContains?: number;
    maxContains?: number;
    uniqueItems?: true;
    additionalItems?: never;
} : T extends Record<string, any> ? {
    type: JSONType<"object", _partial>;
    required: _partial extends true ? (keyof T)[] : RequiredMembers<T>[];
    additionalProperties?: boolean | JSONSchemaType<T[string]>;
    unevaluatedProperties?: boolean | JSONSchemaType<T[string]>;
    properties?: _partial extends true ? Partial<PropertiesSchema<T>> : PropertiesSchema<T>;
    patternProperties?: {
        [Pattern in string]?: JSONSchemaType<T[string]>;
    };
    propertyNames?: JSONSchemaType<string>;
    dependencies?: {
        [K in keyof T]?: (keyof T)[] | PartialSchema<T>;
    };
    dependentRequired?: {
        [K in keyof T]?: (keyof T)[];
    };
    dependentSchemas?: {
        [K in keyof T]?: PartialSchema<T>;
    };
    minProperties?: number;
    maxProperties?: number;
} : T extends null ? {
    nullable: true;
} : never) & {
    [keyword: string]: any;
    $id?: string;
    $ref?: string;
    $defs?: {
        [Key in string]?: JSONSchemaType<Known, true>;
    };
    definitions?: {
        [Key in string]?: JSONSchemaType<Known, true>;
    };
    allOf?: PartialSchema<T>[];
    anyOf?: PartialSchema<T>[];
    oneOf?: PartialSchema<T>[];
    if?: PartialSchema<T>;
    then?: PartialSchema<T>;
    else?: PartialSchema<T>;
    not?: PartialSchema<T>;
};
declare type Known = KnownRecord | [Known, ...Known[]] | Known[] | number | string | boolean | null;
interface KnownRecord extends Record<string, Known> {
}
declare type PropertiesSchema<T> = {
    [K in keyof T]-?: (JSONSchemaType<T[K]> & Nullable<T[K]>) | {
        $ref: string;
    };
};
declare type RequiredMembers<T> = {
    [K in keyof T]-?: undefined extends T[K] ? never : K;
}[keyof T];
declare type Nullable<T> = undefined extends T ? {
    nullable: true;
    const?: never;
    enum?: (T | null)[];
    default?: T | null;
} : {
    const?: T;
    enum?: T[];
    default?: T;
};
export {};
