class ApplicationController < ActionController::Base
  # Prevent CSRF attacks by raising an exception.
  # For APIs, you may want to use :null_session instead.
  protect_from_forgery with: :exception

  def index
    @timestamp = params[:search] || '2016-01-28T12:05:00/2016-01-28T14:05:00'
    @neighs = {}
    @data = {}
    %w(building floor).each do |source|
      data = http_req(dimensions(source)).map { |x| x['event'] }
      @neighs[source] =
        (data.map { |x| x["old_#{source}"] } + data.map { |x| x["new_##{source}"] })
        .uniq.compact.each { |x| x.gsub!('undefined', 'unknown') }
      @data[source] = []
      @neighs[source].each do |neigh|
        sub_data = []
        @neighs[source].each do |sub_neigh|
          data.each do |d|
            if d['old_building'] == neigh && d['new_building'] == sub_neigh
              sub_data << d['clients'].round
              break
            elsif d == data.last 
              sub_data << 0
            end
          end
        end
        @data[source] << sub_data
      end
    end
  end

  private

  def http_req(dim)
    uri =
      URI.parse('http://83.55.109.143:8082/druid/v2/?pretty=true')
    http = Net::HTTP.new(uri.host, uri.port)
    request =
      Net::HTTP::Post.new(uri.path, 'Content-Type' => 'application/json')
    request.body = payload(dim)
    result = http.request(request)
    JSON.parse(result.body)
  end

  def dimensions(source)
    ["new_#{source}", "old_#{source}'"]
  end

  def payload(dim)
    {
      'queryType' => 'groupBy',
        'dataSource' => 'moving',
        'dimensions' => dim,
        'granularity' => 'all',
        'aggregations' => [{
          'type' => 'hyperUnique',
          'fieldName' => 'clients',
          'name' => 'clients' }],
        'intervals' => @timestamp
    }.to_json
  end
end
