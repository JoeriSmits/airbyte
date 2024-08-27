# Copyright (c) 2024 Airbyte, Inc., all rights reserved.
from typing import Any, Dict

from serpyco_rs import CustomType, Serializer

from .airbyte_protocol import (
    AdvancedAuth,
    AirbyteCatalog,
    AirbyteMessage,
    AirbyteStateBlob,
    AirbyteStateMessage,
    AirbyteStreamState,
    ConfiguredAirbyteCatalog,
    ConfiguredAirbyteStream,
    ConnectorSpecification,
)


class AirbyteStateBlobType(CustomType[AirbyteStateBlob, str]):
    def serialize(self, value: AirbyteStateBlob | Dict[str, Any]) -> Dict[str, Any]:
        # cant use orjson.dumps() directly because private attributes are excluded, e.g. "__ab_full_refresh_sync_complete"
        return {k: v for k, v in value.__dict__.items()} if isinstance(value, AirbyteStateBlob) else value

    def deserialize(self, value: Dict[str, Any]) -> AirbyteStateBlob:
        return AirbyteStateBlob(value)

    def get_json_schema(self):
        return {"type": "object"}


def custom_type_resolver(t: type) -> CustomType | None:
    return AirbyteStateBlobType() if t is AirbyteStateBlob else None


AirbyteStreamStateSerializer = Serializer(AirbyteStreamState, omit_none=True, custom_type_resolver=custom_type_resolver)
AirbyteStateMessageSerializer = Serializer(AirbyteStateMessage, omit_none=True, custom_type_resolver=custom_type_resolver)
AirbyteMessageSerializer = Serializer(AirbyteMessage, omit_none=True, custom_type_resolver=custom_type_resolver)
AirbyteCatalogSerializer = Serializer(AirbyteCatalog, omit_none=True)
ConfiguredAirbyteCatalogSerializer = Serializer(ConfiguredAirbyteCatalog, omit_none=True)
ConfiguredAirbyteStreamSerializer = Serializer(ConfiguredAirbyteStream, omit_none=True)
AdvancedAuthSerializer = Serializer(AdvancedAuth, omit_none=True)
ConnectorSpecificationSerializer = Serializer(ConnectorSpecification, omit_none=True)