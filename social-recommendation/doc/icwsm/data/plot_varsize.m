function [] = plot_varsize(name, prec, plot_title, legend_pos, subfig)

fontsize = 14;

data = [];
varname = strtrim(['varsize_',name, '_', num2str(prec)]);
disp(['load ',varname,'.txt']);
eval(['load ',varname,'.txt']);
str = ['data=',varname,';']
eval(str);

figure(3);

if (subfig > 0)
    subplot(1,3,subfig);
end

hold off
plot(data(:,1),data(:,2)./100,'ro-');
hold on
plot(data(:,1),data(:,5)./100,'bs--');
max_time = max(max(data(:,2)),max(data(:,5)))./100;
xlabel('Problem Size','FontSize',fontsize);
if (subfig == 1)
    legend('APRICODD (ADD)','MADCAP (AADD)','FontSize',fontsize,'Location',legend_pos);
    ylabel('Execution Time (s)','FontSize',fontsize);
end
title(plot_title,'FontSize',fontsize);
%axis tight;
axis([6,12,0,max_time.*1.1]);

print(gcf, '-depsc', [varname,'_time.eps']);
fixPSlinestyle([varname,'_time.eps'],[varname,'_time_fix.eps']);

figure(4);
if (subfig > 0)
    subplot(1,3,subfig);
end

hold off
plot(data(:,1),data(:,3),'ro-');
hold on
plot(data(:,1),data(:,6),'bs--');
max_space = max(max(data(:,3)),max(data(:,6)));
xlabel('Problem Size','FontSize',fontsize);
if (subfig == 1)
    legend('APRICODD (ADD)','MADCAP (AADD)','FontSize',fontsize,'Location',legend_pos);
    ylabel('Space (# Nodes)','FontSize',fontsize);
end
%title(plot_title,'FontSize',fontsize);
axis tight;
axis([6,12,0,max_space.*1.1]);

print(gcf, '-depsc', [varname,'_space.eps']);
fixPSlinestyle([varname,'_space.eps'],[varname,'_space_fix.eps']);

figure(5);
if (subfig > 0)
    subplot(1,3,subfig);
end

hold off
plot(data(:,1),data(:,4),'ro-');
hold on
plot(data(:,1),data(:,7),'bs--');
max_space = max(max(data(:,4)),max(data(:,7)));
xlabel('Problem Size','FontSize',fontsize);
if (subfig == 1)
    legend('APRICODD (ADD)','MADCAP (AADD)','FontSize',fontsize,'Location','SouthEast');
    ylabel('True Approximation Error','FontSize',fontsize-2);
end
%title(plot_title,'FontSize',fontsize);
axis tight;
axis([6,12,0,max_space.*1.1]);

print(gcf, '-depsc', [varname,'_error.eps']);
fixPSlinestyle([varname,'_error.eps'],[varname,'_error_fix.eps']);


