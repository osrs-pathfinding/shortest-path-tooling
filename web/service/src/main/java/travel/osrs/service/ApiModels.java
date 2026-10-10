package travel.osrs.service;

/** The service's own response bodies; route requests and plans are {@link shortestpath.routeapi.RouteApi}. */
public final class ApiModels
{
	private ApiModels()
	{
	}

	public static final class ErrorResponse
	{
		public final String error;
		public final String requestId;

		public ErrorResponse(String error, String requestId)
		{
			this.error = error;
			this.requestId = requestId;
		}
	}

	public static final class ItemOption
	{
		public final String key;
		public final String name;

		public ItemOption(String key, String name)
		{
			this.key = key;
			this.name = name;
		}
	}
}
